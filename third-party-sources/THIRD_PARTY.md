# Bundled software

The Android carrier source is MIT licensed. Separate bundled programs retain their own licenses.

- DeepSeek Harness 0.2.0-rc.2: MIT, https://github.com/deepseek-ai/deepseek-harness . Published npm packages, their LICENSE files and package metadata are present inside the runtime under `/opt/dsh/node_modules`. Native system primitives use BSD-3-Clause.
- Node.js 24.9.0: MIT and third-party notices, https://nodejs.org/dist/v24.9.0/ . Its LICENSE and headers are present under `/usr/local`.
- pnpm 11.28.5: MIT, https://github.com/pnpm/pnpm . Package license is under `/opt/package-manager/node_modules/pnpm`.
- Ubuntu Base 24.04.3 ARM64 plus Ubuntu noble packages: component-specific licenses in `/usr/share/doc/*/copyright`, with package names, source versions and installed binary versions in `/var/lib/dpkg/status`. Corresponding source package records are available at `https://launchpad.net/ubuntu/+source/<source-package>/<source-version>` and Ubuntu archive source indices at `https://ports.ubuntu.com/ubuntu-ports/dists/`. Package source inventory is in `ubuntu-source-inventory.json`.
- Termux PRoot 5.1.107.96: GPL-2.0-or-later. Exact source archive is included in `third-party-sources`, together with Termux build recipes. https://github.com/termux/proot/tree/v5.1.107.96 . The APK uses a modified PRoot executable and matching loader rebuilt with Android NDK r27d. Changes: `third-party-sources/proot-dirent-types.patch`; full build recipe: `scripts/build_proot_android.py`. Only emulated hardlinks receive DT_UNKNOWN in getdents/getdents64; genuine symlinks are unchanged. The build recipe also generates the loader symbol-offset helper and compiler wrapper.
- talloc 2.5.0: upstream LGPL-3.0-or-later; see the source archive license notices. Exact source archive and Termux build recipe are included. https://www.samba.org/ftp/talloc/talloc-2.5.0.tar.gz . The bundled Termux binary is unmodified.
- libandroid-shmem 0.7: BSD-3-Clause. Exact source archive and Termux build recipe are included. https://github.com/termux/libandroid-shmem/tree/v0.7 . The bundled Termux binary is unmodified.

Native Termux packages used:

| Package | SHA256 of downloaded .deb |
| --- | --- |
| proot_5.1.107.96_aarch64.deb | 8199dca06dccb693ec09fb1759e3e1ad08b4863f0c11c612f89c20bd9ecdc1a0 |
| libtalloc_2.5.0_aarch64.deb | 556591f43bb773ad8777e1a29522640866a55f95dab71914418b94a8c58ad5a7 |
| libandroid-shmem_0.7_aarch64.deb | 0da3a24d558b93c92bcf8d611e0826a99ff96e396b148e6cdf33b47c47c57ff6 |

The native executable is renamed to `libproot.so` for Android packaging. `libtalloc.so.2` is provided through a symlink to the packaged `libtalloc.so`; no ELF contents are changed. Corresponding sources for these three programs are also included as `assets/third-party-sources.zip` in the APK. Neither the Android SDK nor the host QEMU testing tools are distributed in the APK.

## SSH update payload

The APK additionally ships `ssh-tools-v1.zip`, extracted from official Ubuntu noble ARM64 packages. Exact versions, download URLs and hashes are in `app/src/main/assets/ssh-tools-packages.json`. Copyright/license notices are inside the payload under `usr/share/doc`.

- OpenSSH client 9.6p1-3ubuntu13.19: BSD-style licenses, source https://launchpad.net/ubuntu/+source/openssh/1:9.6p1-3ubuntu13.19 .
- sshpass 1.09-1: GPL-2.0-or-later; corresponding upstream and Debian source archives included in `third-party-sources/sshpass/`.
- libedit 3.1-20230828-1build1: BSD-style, source https://launchpad.net/ubuntu/+source/libedit/3.1-20230828-1build1 .
- libbsd 0.12.1-1build1.1: component-specific BSD-style licenses, source https://launchpad.net/ubuntu/+source/libbsd/0.12.1-1build1.1 .
- libfido2 1.14.0-1build3: BSD-2-Clause, source https://launchpad.net/ubuntu/+source/libfido2/1.14.0-1build3 .
- libcbor 0.10.2-1.2ubuntu2: MIT, source https://launchpad.net/ubuntu/+source/libcbor/0.10.2-1.2ubuntu2 .

The MIT license at repository root applies to the carrier and our patches, not to all bundled software collectively. The PRoot binaries remain GPL licensed; talloc retains its upstream LGPL license and source notices. All included third-party source archives remain under their original licenses.
