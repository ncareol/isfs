#! /opt/local/anaconda3/bin/python3

"""
Mount any attached removable data disks and rsync the contents to the
given location.

XXX
According to the blkid man page, lsblk should be used instead.

"""

import os
import sys
import re
import subprocess as sp
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

    def __init__(self, context, device, mountpoint=None, uuid=None):
        "Initialize a device with initial state as unmounted."
        self.context = context
        self.device = device
        self.mountpoint = mountpoint
        self.uuid = uuid

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
        self.no_uuid = False

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
        prsync.add_argument('--no-uuid', dest="no_uuid",
                            action="store_true", default=False,
                            help="Do not append device UUID to "
                            "destination path.")
        prsync.add_argument('src', help=textwrap.dedent("""
        Relative path to source directory on USB stick, 
        such as projects/RELAMPAGO/raw_data"""))
        prsync.add_argument('dest', help='Path to rsync dest directory.')
        for op in [plist, pmount, punmount, pclear]:
            op.add_argument('device', nargs='*')

    def dispatch(self, args: argparse.Namespace):
        self.src = args.src
        self.dest = args.dest
        self.dryrun = args.dryrun
        self.no_uuid = vars(args).get('no_uuid', self.no_uuid)
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
                raise Exception("Data device '%s' not found." % (d))
        op = args.operation
        if op == 'list':
            self._each_device(names, self.listDevice)
        elif op == 'devices':
            print("".join(["%-10s%s\n" % (k, d.mountpoint)
                           for k, d in self.devices.items()]), end='')
        elif op == 'rsync':
            self._rsync([self.devices[name] for name in names], args.remove)
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
        instead.  Calls with check=True to raise a CalledProcessError if
        the command fails.  If capture_output is not passed in, it is set
        to True.  So by default callers can get the output from the stdout
        member of the returned CompletedProcess instance.  If keyword arg
        safe is True, then the command is run even if dryrun is enabled.
        If keyword asynck is True, then return the subprocess.Popen object
        for the process running in the background.
        """
        if cmd is type([]):
            scmd = "%s" % (" ".join(cmd))
        else:
            scmd = "%s" % (cmd)
        logger.info("%s", scmd)
        # Check for dryrun mode or for commands safe to run in dryrun mode.
        safe = kwargs.get('safe')
        if 'safe' in kwargs:
            del kwargs['safe']
        echo = []
        if self.dryrun and not safe:
            echo = ['echo']
        # Capture output by default, but using the 3.5 version of
        # subprocess API.
        if kwargs.get('capture_output', True):
            kwargs['stdout'] = sp.PIPE
        if 'capture_output' in kwargs:
            del kwargs['capture_output']
        asynck = kwargs.get('asynck')
        if 'asynck' in kwargs:
            del kwargs['asynck']
        if asynck:
            return sp.Popen(echo+cmd, universal_newlines=True, **kwargs)
        return sp.run(echo+cmd, universal_newlines=True, **kwargs)


    def loadDevices(self, blkid=None, mount=None):
        """
        Search for data disks on the system and load their current status,
        such as whether they are mounted or not and where.  Data disks
        are identified by LABEL=data.
        """
        px = re.compile(r"^(?P<device>[/a-zA-Z0-9]+):.*\s+"
                        r"LABEL=\"data\".*\s+UUID=\"(?P<uuid>\S+)\"\s.*"
                        r"TYPE=\"ext4\".*$")
        if blkid is None:
            blkid = self._run(["blkid"], safe=True).stdout
        devices = collections.OrderedDict()
        for line in blkid.splitlines():
            rx = px.match(line)
            if rx:
                device = DataDevice(self, rx.group('device'))
                device.uuid = rx.group('uuid')
                devices[device.device] = device
        if not devices:
            logger.error("No data devices found.")
        else:
            logger.info("Data devices: %s", ",".join(devices))

        # Now match up an existing mount point with each usb data device
        pxm = re.compile(r"^(?P<device>[/a-zA-Z0-9]+) on "
                         r"(?P<mountpoint>[/a-zA-Z0-9_]+) type .*$")
        if mount is None:
            mount = self._run(["mount"], safe=True).stdout
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

    def _rsync(self, devices, remove=False):
        procs = []
        for device in devices:
            mountpoint = device.mount()
            cmd = ["rsync", "-av"]
            if self.dryrun:
                cmd += ["--dry-run"]
            if remove:
                cmd += ['--remove-source-files']
            src = os.path.join(mountpoint, self.src)
            src += "/" if not src.endswith("/") else ""
            cmd += [src]
            # Append UUID to the dest path unless disabled.
            dest = os.path.join(self.dest, device.uuid)
            if self.no_uuid:
                dest = self.dest
            cmd += [dest]
            procs.append(self._run(cmd, safe=True, asynck=True,
                                   capture_output=False))
        for i, device in enumerate(devices):
            procs[i].wait()
            device.unmount()

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




_test_data = [
    (
        "A test of full unabridged outputs.",
        """\
/dev/nvme0n1p1: SEC_TYPE="msdos" UUID="88BD-6034" TYPE="vfat" PARTLABEL="EFI System Partition" PARTUUID="55a5f402-ffab-46ad-a840-a47ac011d1e1"
/dev/nvme0n1p2: UUID="dcc3a095-17fe-4668-a176-31279f0e4f3b" TYPE="ext4" PARTUUID="09833ce0-426c-4ae9-bc6a-040bb91ba624"
/dev/nvme0n1p3: UUID="UAY5lp-xUd9-zZ67-aMd8-VIEP-ZoY6-fTqhI7" TYPE="LVM2_member" PARTUUID="194b7a2e-a76c-4c01-a709-60a28489e934"
/dev/mapper/fedora-root: UUID="31ac66e3-b8e4-4b9f-8399-5caf6613cf8b" TYPE="ext4"
/dev/mapper/fedora-swap: UUID="84014897-4100-4fbf-90e4-bb1369c3a6d6" TYPE="swap"
/dev/mapper/fedora-home: UUID="f706e3ce-184b-44a2-810a-578464e77d77" TYPE="ext4"
/dev/sdb1: LABEL="data" UUID="8a87b6d9-844a-4c74-92f7-6887d0ae8a82" TYPE="ext4"
/dev/sdc1: LABEL="data" UUID="8064f59e-d185-495a-b7a1-93acf0ce80cc" TYPE="ext4"
/dev/sda1: LABEL="USB_RELAMPAGO" UUID="a9cd0154-0c20-4784-b331-485b48711444" TYPE="ext2" PARTUUID="947922f0-01"
/dev/nvme0n1: PTUUID="26c6a886-b7b1-4637-8b38-62d42abf6e3a" PTTYPE="gpt"
""",
        """\
sysfs on /sys type sysfs (rw,nosuid,nodev,noexec,relatime,seclabel)
proc on /proc type proc (rw,nosuid,nodev,noexec,relatime)
devtmpfs on /dev type devtmpfs (rw,nosuid,seclabel,size=8113660k,nr_inodes=2028415,mode=755)
securityfs on /sys/kernel/security type securityfs (rw,nosuid,nodev,noexec,relatime)
tmpfs on /dev/shm type tmpfs (rw,nosuid,nodev,seclabel)
devpts on /dev/pts type devpts (rw,nosuid,noexec,relatime,seclabel,gid=5,mode=620,ptmxmode=000)
tmpfs on /run type tmpfs (rw,nosuid,nodev,seclabel,mode=755)
tmpfs on /sys/fs/cgroup type tmpfs (ro,nosuid,nodev,noexec,seclabel,mode=755)
cgroup2 on /sys/fs/cgroup/unified type cgroup2 (rw,nosuid,nodev,noexec,relatime,seclabel,nsdelegate)
cgroup on /sys/fs/cgroup/systemd type cgroup (rw,nosuid,nodev,noexec,relatime,seclabel,xattr,name=systemd)
pstore on /sys/fs/pstore type pstore (rw,nosuid,nodev,noexec,relatime,seclabel)
efivarfs on /sys/firmware/efi/efivars type efivarfs (rw,nosuid,nodev,noexec,relatime)
bpf on /sys/fs/bpf type bpf (rw,nosuid,nodev,noexec,relatime,mode=700)
cgroup on /sys/fs/cgroup/hugetlb type cgroup (rw,nosuid,nodev,noexec,relatime,seclabel,hugetlb)
cgroup on /sys/fs/cgroup/devices type cgroup (rw,nosuid,nodev,noexec,relatime,seclabel,devices)
cgroup on /sys/fs/cgroup/freezer type cgroup (rw,nosuid,nodev,noexec,relatime,seclabel,freezer)
cgroup on /sys/fs/cgroup/cpu,cpuacct type cgroup (rw,nosuid,nodev,noexec,relatime,seclabel,cpu,cpuacct)
cgroup on /sys/fs/cgroup/pids type cgroup (rw,nosuid,nodev,noexec,relatime,seclabel,pids)
cgroup on /sys/fs/cgroup/blkio type cgroup (rw,nosuid,nodev,noexec,relatime,seclabel,blkio)
cgroup on /sys/fs/cgroup/cpuset type cgroup (rw,nosuid,nodev,noexec,relatime,seclabel,cpuset)
cgroup on /sys/fs/cgroup/net_cls,net_prio type cgroup (rw,nosuid,nodev,noexec,relatime,seclabel,net_cls,net_prio)
cgroup on /sys/fs/cgroup/perf_event type cgroup (rw,nosuid,nodev,noexec,relatime,seclabel,perf_event)
cgroup on /sys/fs/cgroup/memory type cgroup (rw,nosuid,nodev,noexec,relatime,seclabel,memory)
configfs on /sys/kernel/config type configfs (rw,relatime)
/dev/mapper/fedora-root on / type ext4 (rw,relatime,seclabel)
selinuxfs on /sys/fs/selinux type selinuxfs (rw,relatime)
systemd-1 on /proc/sys/fs/binfmt_misc type autofs (rw,relatime,fd=32,pgrp=1,timeout=0,minproto=5,maxproto=5,direct,pipe_ino=16731)
debugfs on /sys/kernel/debug type debugfs (rw,relatime,seclabel)
mqueue on /dev/mqueue type mqueue (rw,relatime,seclabel)
hugetlbfs on /dev/hugepages type hugetlbfs (rw,relatime,seclabel,pagesize=2M)
tmpfs on /tmp type tmpfs (rw,nosuid,nodev,seclabel)
fusectl on /sys/fs/fuse/connections type fusectl (rw,relatime)
/dev/nvme0n1p2 on /boot type ext4 (rw,relatime,seclabel)
/dev/mapper/fedora-home on /home type ext4 (rw,relatime,seclabel)
/dev/nvme0n1p1 on /boot/efi type vfat (rw,relatime,fmask=0077,dmask=0077,codepage=437,iocharset=ascii,shortname=winnt,errors=remount-ro)
sunrpc on /var/lib/nfs/rpc_pipefs type rpc_pipefs (rw,relatime)
/dev/mapper/fedora-root on /var/lib/docker/containers type ext4 (rw,relatime,seclabel)
/dev/mapper/fedora-root on /var/lib/docker/overlay2 type ext4 (rw,relatime,seclabel)
tmpfs on /run/user/1000 type tmpfs (rw,nosuid,nodev,relatime,seclabel,size=1625872k,mode=700,uid=1000,gid=1000)
gvfsd-fuse on /run/user/1000/gvfs type fuse.gvfsd-fuse (rw,nosuid,nodev,relatime,user_id=1000,group_id=1000)
/dev/sda1 on /run/media/granger/USB_RELAMPAGO type ext2 (rw,nosuid,nodev,relatime,seclabel,uhelper=udisks2)
""",
        [DataDevice(None, "/dev/sdb1", None,
                    "8a87b6d9-844a-4c74-92f7-6887d0ae8a82"),
         DataDevice(None, "/dev/sdc1", None,
                    "8064f59e-d185-495a-b7a1-93acf0ce80cc")]
    ),
    (
        "Trimmed down to the important parts, but no change in output.",
        """\
/dev/sdb1: LABEL="data" UUID="8a87b6d9-844a-4c74-92f7-6887d0ae8a82" TYPE="ext4"
/dev/sdc1: LABEL="data" UUID="8064f59e-d185-495a-b7a1-93acf0ce80cc" TYPE="ext4"
/dev/sda1: LABEL="USB_RELAMPAGO" UUID="a9cd0154-0c20-4784-b331-485b48711444" TYPE="ext2" PARTUUID="947922f0-01"
""",
        """\
/dev/sda1 on /run/media/granger/USB_RELAMPAGO type ext2 (rw,nosuid,nodev,relatime,seclabel,uhelper=udisks2)
""",
        [DataDevice(None, "/dev/sdb1", None,
                    "8a87b6d9-844a-4c74-92f7-6887d0ae8a82"),
         DataDevice(None, "/dev/sdc1", None,
                    "8064f59e-d185-495a-b7a1-93acf0ce80cc")]
    ),
    (
        "Test already mounted.",
        """\
/dev/sdb1: LABEL="data" UUID="8a87b6d9-844a-4c74-92f7-6887d0ae8a82" TYPE="ext4"
/dev/sdc1: LABEL="data" UUID="8064f59e-d185-495a-b7a1-93acf0ce80cc" TYPE="ext4"
/dev/sda1: LABEL="USB_RELAMPAGO" UUID="a9cd0154-0c20-4784-b331-485b48711444" TYPE="ext2" PARTUUID="947922f0-01"
""",
        """\
tmpfs on /run/user/1000 type tmpfs (rw,nosuid,nodev,relatime,seclabel,size=1625872k,mode=700,uid=1000,gid=1000)
gvfsd-fuse on /run/user/1000/gvfs type fuse.gvfsd-fuse (rw,nosuid,nodev,relatime,user_id=1000,group_id=1000)
/dev/sda1 on /run/media/granger/USB_RELAMPAGO type ext2 (rw,nosuid,nodev,relatime,seclabel,uhelper=udisks2)
/dev/sdb1 on /tmp/reclaim_sdb1 type ext4 (rw,relatime,seclabel)
/dev/sdc1 on /tmp/reclaim_sdc1 type ext4 (rw,relatime,seclabel)
""",
        [DataDevice(None, "/dev/sdb1", "/tmp/reclaim_sdb1",
                    "8a87b6d9-844a-4c74-92f7-6887d0ae8a82"),
         DataDevice(None, "/dev/sdc1", "/tmp/reclaim_sdc1",
                    "8064f59e-d185-495a-b7a1-93acf0ce80cc")]
    ),
    (
        "Test already mounted but returned in different order.",
        """\
/dev/sdb1: LABEL="data" UUID="8a87b6d9-844a-4c74-92f7-6887d0ae8a82" TYPE="ext4"
/dev/sdc1: LABEL="data" UUID="8064f59e-d185-495a-b7a1-93acf0ce80cc" TYPE="ext4"
/dev/sda1: LABEL="USB_RELAMPAGO" UUID="a9cd0154-0c20-4784-b331-485b48711444" TYPE="ext2" PARTUUID="947922f0-01"
""",
        """\
/dev/sdc1 on /tmp/reclaim_sdc1 type ext4 (rw,relatime,seclabel)
tmpfs on /run/user/1000 type tmpfs (rw,nosuid,nodev,relatime,seclabel,size=1625872k,mode=700,uid=1000,gid=1000)
gvfsd-fuse on /run/user/1000/gvfs type fuse.gvfsd-fuse (rw,nosuid,nodev,relatime,user_id=1000,group_id=1000)
/dev/sda1 on /run/media/granger/USB_RELAMPAGO type ext2 (rw,nosuid,nodev,relatime,seclabel,uhelper=udisks2)
/dev/sdb1 on /tmp/reclaim_sdb1 type ext4 (rw,relatime,seclabel)
""",
        [DataDevice(None, "/dev/sdb1", "/tmp/reclaim_sdb1",
                    "8a87b6d9-844a-4c74-92f7-6887d0ae8a82"),
         DataDevice(None, "/dev/sdc1", "/tmp/reclaim_sdc1",
                    "8064f59e-d185-495a-b7a1-93acf0ce80cc")]
    ),

]


import pytest

@pytest.mark.parametrize("id,blkid,mount,expected", _test_data,
                         ids=[td[0] for td in _test_data])
def test_loadDevices(id, blkid, mount, expected):
    reclaim = ReclaimUSB()
    devices = reclaim.loadDevices(blkid, mount)
    for i, d in enumerate(devices.values()):
        d2 = expected[i]
        assert d.device == d2.device
        assert d.mountpoint == d2.mountpoint
        assert d.uuid == d2.uuid
