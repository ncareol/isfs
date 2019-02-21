#! /usr/bin/python3

"""
Mount any attached removable data disks and rsync the contents to the
given location.
"""

import os
import sys
import re
import subprocess as sp
import shutil
import argparse
import textwrap
import collections
import logging

logger = logging.getLogger("reclaim_usb")


class DataDevice:
    """
    Keep status information for a particular data disk device,
    identified by it's device path.  context is a parent object with a
    _run() method for running system processes.  If the device is
    mounted, then the mountpoint will be set, otherwise it will be
    None.
    """

    def __init__(self, context, device):
        "Initialize a device with initial state as unmounted."
        self.context = context
        self.device = device
        self.mountpoint = None

    def _generate_mountpoint(self):
        "Return the device-specific path to mount this device."
        mountpoint = "/tmp/reclaim_%s" % (os.path.basename(self.device))
        return mountpoint

    def mount(self):
        "Mount the device and return the mountpoint."
        if self.mountpoint == self._generate_mountpoint():
            logger.info("Device %s already mounted.", self.device)
            return self.mountpoint
        if self.mountpoint:
            # Mounted, but at the wrong place, so fail.
            raise Exception("device '%s' is mounted but at an "
                            "unexpected mountpoint" % (self.device))
        mountpoint = self._generate_mountpoint()
        if not self.context.dryrun:
            os.makedirs(mountpoint, exist_ok=True)
        logger.info("%s Mounting %s on %s", "="*20, self.device, mountpoint)
        self.context._run(["mount", self.device, mountpoint], capture_output=False)
        self.mountpoint = mountpoint
        return mountpoint

    def unmount(self):
        "Unmounted the device if currently mounted."
        if self.mountpoint:
            self.context._run(["umount", self.device], capture_output=False)
            self.mountpoint = None
        mountpoint = self._generate_mountpoint()
        # As long as the mountpoint is now an empty directory, we can
        # remove it with rmdir().  If not, it will fail.  This is much
        # safer than shutil.rmtree(), since that risks removing
        # everything on the disk because it was still mounted under
        # mountpoint.  It would also be reasonable to just leave the
        # mountpoints there since they are in /tmp.
        if os.path.exists(mountpoint) and not self.context.dryrun:
            os.rmdir(mountpoint)


class ReclaimUSB:

    """
    Operations for finding, mounting, rsync'ing, and clearing ISFS data
    disks.
    """

    def __init__(self):
        # List of devices for ISFS USB sticks attached to the system.
        self.devices = None
        # The relative path to the source directory on each USB.
        self.src = None
        # The path to the rsync destination directory.
        self.dest = None
        self.dryrun = False

    def addArgs(self, parser):
        # So all args have src and dest, even though only required by rsync
        # operation:
        parser.set_defaults(src=None, dest=None, device=None, target=None)
        parser.add_argument("--dryrun", action="store_true",
                            help="Only echo commands, do not run them.")
        subparsers = parser.add_subparsers(dest="operation")
        (plist, prsync, pclear, pmount, punmount, pdevices) = [
            subparsers.add_parser(name) for name in
            ['list', 'rsync', 'clear', 'mount', 'unmount', 'devices']]
        prsync.add_argument("--device", "-d", dest='target',
                            help="Limit to the single named device.")
        prsync.add_argument('--remove', action="store_true", default=False,
                            help="Remove source files after "
                            "synchronizing them.")
        prsync.add_argument('src', help=textwrap.dedent("""
        Relative path to source directory on USB stick, 
        such as projects/RELAMPAGO/raw_data"""))
        prsync.add_argument('dest', help='Path to rsync dest directory.')
        for op in [plist, pmount, punmount, pclear]:
            op.add_argument('device', nargs='*')

    def dispatch(self, args):
        self.src = args.src
        self.dest = args.dest
        self.dryrun = args.dryrun
        self.loadDevices()
        names = []
        if args.target:
            names = [args.target]
        elif args.device:
            names = args.device
        else:
            names = self.devices.keys()
        for d in names:
            if d not in self.devices:
                if self.dryrun:
                    logger.info("faking device '%s' for dry run.", d)
                    self.devices[d] = DataDevice(self, d)
                else:
                    raise Exception("Data device '%s' not found." % (d))
        op = args.operation
        if op == 'list':
            self._each_device(names, self.listDevice)
        elif op == 'devices':
            print("".join(["%-10s%s\n" % (k, d.mountpoint)
                           for k, d in self.devices.items()]), end='')
        elif op == 'rsync':
            self._each_device(names, lambda device:
                              self._rsync(device, args.remove))
        elif op == 'clear':
            self._each_device(names, self.clearProjects)
        elif op == 'mount':
            self._each_device(names, lambda device: device.mount())
        elif op == 'unmount':
            self._each_device(names, lambda device: device.unmount())
        else:
            raise Exception("Unknown operation: %s" % (op))

    def _each_device(self, names, op):
        for name in names:
            op(self.devices[name])

    def _run(self, cmd, **kwargs):
        """
        Run the command arguments in cmd and return the CompletedProcess
        instance.  If dryrun is enabled, echo the command arguments
        instead.  Calls with check=True to raise a CalledProcessError 
        if the command fails.  If capture_output is not passed in, it
        is set to True.  So by default callers can get the output from
        the stdout member of the returned CompletedProcess instance.
        """
        if cmd is type([]):
            scmd = "%s" % (" ".join(cmd))
        else:
            scmd = "%s" % (cmd)
        # if self.dryrun:
        #     print("%s" % (scmd))
        #     return ""
        logger.info("%s", scmd)
        echo = []
        if self.dryrun:
            echo = ['echo']
        # Capture output by default, but using the 3.5 version of
        # subprocess API.
        if kwargs.get('capture_output', True):
            kwargs['stdout'] = sp.PIPE
        if 'capture_output' in kwargs:
            del kwargs['capture_output']
        return sp.run(echo+cmd, universal_newlines=True, **kwargs)

    def loadDevices(self):
        """
        Search for data disks on the system and load their current status,
        such as whether they are mounted or not and where.  Data disks
        are identified by LABEL=data.
        """
        px = re.compile(r"^(?P<device>[/a-zA-Z0-9]+):.* "
                        r"LABEL=\"data\" .*TYPE=\"ext4\".*$")
        blkid = self._run(["blkid"]).stdout
        devices = collections.OrderedDict()
        for line in blkid.splitlines():
            rx = px.match(line)
            if rx:
                device = DataDevice(self, rx.group('device'))
                devices[device.device] = device
        if not devices:
            logger.error("No data devices found.")
        else:
            logger.info("Data devices: %s", ",".join(devices))
        pxm = re.compile(r"^(?P<device>[/a-zA-Z0-9]+) on "
                         r"(?P<mountpoint>[/a-zA-Z0-9_]+) type .*$")
        mount = self._run(["mount"]).stdout
        for line in mount.splitlines():
            rx = pxm.match(line)
            if rx:
                # logger.debug("mount output matched '%s' on '%s'",
                # rx.group('device'), rx.group('mountpoint'))
                device = devices.get(rx.group('device'))
                if device:
                    device.mountpoint = rx.group('mountpoint')
        self.devices = devices
        return devices

    def listDevice(self, device):
        device.mount()
        self._run(["ls", "-RlaF", device.mountpoint], capture_output=False)
        device.unmount()

    def _rsync(self, device, remove=False):
        mountpoint = device.mount()
        cmd = ["rsync", "-av"]
        if remove:
            cmd += ['--remove-source-files']
        cmd += [os.path.join(mountpoint, self.src), self.dest]
        self._run(cmd, capture_output=False)
        device.unmount()

    def rsyncData(self, device):
        self._rsync(device)

    def rsyncRemove(self, device):
        self._rsync(device, remove=True)

    def clearProjects(self, device):
        """
        Clear the project directories on the data device, but only the ones
        that are empty.  In other words, this will fail if anything is left
        under the project directory after removing empty raw_data
        directories.
        """
        mountpoint = device.mount()
        projects = []
        projpath = os.path.join(mountpoint, "projects")
        with os.scandir(projpath) as pit:
            for entry in pit:
                projects.append(entry.name)
        logger.info("projects subdirectories: %s", ",".join(projects))
        for p in projects:
            pdir = os.path.join(projpath, p)
            rdp = os.path.join(pdir, "raw_data")
            os.rmdir(rdp)
            os.rmdir(pdir)
        os.chmod(projpath, 0o775)
        device.unmount()


def main():
    logging.basicConfig(level=logging.DEBUG)
    parser = argparse.ArgumentParser(description="""

Recover data from ISFS USB drives with rsync and prepare the drives for
reuse.

If a device is given, then mount and rsync the given device.  Otherwise
search for attached devices with volume name 'data'.  The rsync_remove adds
the option to --remove-source-files option rsync to remove files after
synchronization.  clear_projects removes all empty raw_data directories and
directories under projects/ on the data device.""")
    reclaim = ReclaimUSB()
    reclaim.addArgs(parser)
    args = parser.parse_args()
    if not args.operation:
        print("Operation required.  Run with -h for usage info.")
        sys.exit(1)
    reclaim.dispatch(args)


if __name__ == "__main__":
    main()
