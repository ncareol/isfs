#! /usr/bin/python3

"""
Mount any attached removable data disks and rsync the contents to the
given location.
"""

import os
import subprocess as sp
import shutil
import argparse
import textwrap
import collections
import logging

logger = logging.getLogger("reclaim_usb")


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
        self.ops = collections.OrderedDict()
        self.ops['list'] = (lambda device, args: self.listDevice(device))
        self.ops['rsync'] = (lambda device, args:
                             self._rsync(device, args.remove))
        self.ops['clear'] = (lambda device, args: self.clearProjects(device))
        self.ops['mount'] = (lambda device, args: self.mountDevice(device))
        self.ops['unmount'] = (lambda device, args: self.unmountDevice(device))

    def addArgs(self, parser):
        # So all args have src and dest, even though only required by rsync
        # operation:
        parser.set_defaults(src=None, dest=None, device=None, target=None)
        parser.add_argument("--dryrun", action="store_true",
                            help="Only echo commands, do not run them.")
        subparsers = parser.add_subparsers(dest="operation")
        (plist, prsync, pclear, pmount, punmount) = [
            subparsers.add_parser(name) for name in
            ['list', 'rsync', 'clear', 'mount', 'unmount']]
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
        if args.target:
            self.devices = [args.target]
        elif args.device:
            self.devices = args.device
        else:
            self.devices = self.findDevices()
        for device in self.devices:
            self.ops[args.operation](device, args)

    def _run(self, cmd):
        if self.dryrun:
            print("%s" % (" ".join(cmd)))
            return ""
        logger.info("%s" % (" ".join(cmd)))
        return sp.check_output(cmd)

    def findDevices(self):
        """
        Mount any attached removable data disks and rsync the contents to the
        given location.
        """
        devices = sp.check_output("blkid | grep 'LABEL=\"data\"' | "
                                  "sed -e 's/:.*//'", shell=True).split()
        if not devices:
            logger.error("No data devices found.")
        else:
            logger.info("Data devices: %s", ",".join(self.devices))
        return devices

    def listDevice(self, device):
        mountpoint = self.mountDevice(device)
        self._run(["ls", "-RlaF", mountpoint])
        self.unmountDevice(device)

    def mountpoint(self, device):
        "Return the device-specific path to mount this device."
        mountpoint = "/tmp/reclaim_%s" % (os.path.basename(device))
        return mountpoint

    def mountDevice(self, device):
        "Mount the device and return the mountpoint."
        mountpoint = self.mountpoint(device)
        os.makedirs(mountpoint, exist_ok=True)
        logger.info("%s Mounting %s on %s" %("="*20, device, mountpoint))
        self._run(["mount", device, mountpoint])
        return mountpoint

    def unmountDevice(self, device):
        mountpoint = self.mountpoint(device)
        self._run(["umount", device])
        shutil.rmtree(mountpoint, ignore_errors=True)

    def _rsync(self, device, remove=False):
        mountpoint = self.mountDevice(device)
        cmd = ["rsync", "-av"]
        if remove:
            cmd += ['--remove-source-files']
        cmd += [os.path.join(mountpoint, self.src), self.dest]
        self._run(cmd)
        self.unmountDevice(device)

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
        mountpoint = self.mountDevice(device)
        projects = []
        projpath = os.path.join(mountpoint, "projects")
        with os.scandir(projpath) as pit:
            for entry in pit:
                projects.append(entry.name)
        logger.info("projects subdirectories: %s" % (",".join(projects)))
        for p in projects:
            pdir = os.path.join(projpath, p)
            rdp = os.path.join(pdir, "raw_data")
            os.rmdir(rdp)
            os.rmdir(pdir)
        os.chmod(projpath, 0o775)
        self.unmountDevice(device)


def main():
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
    reclaim.dispatch(args)


if __name__ == "__main__":
    main()
