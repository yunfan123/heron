#!/bin/bash -e

# Copyright 2015 The Bazel Authors. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Heron self-extractable installer for api package

# Installation and etc prefix can be overriden from command line
install_prefix=${1:-"/usr/local/heronapi"}

progname="$0"

echo "Heron API installer"
echo "---------------------"
echo
cat <<'EOF'
%release_info%
EOF

function usage() {
  echo "Usage: $progname [options]" >&2
  echo "Options are:" >&2
  echo "  --prefix=/some/path set the prefix path (default=/usr/local)." >&2
  echo "  --user configure for user install, expands to" >&2
  echo '           `--prefix=$HOME/.heronapi`.' >&2
  exit 1
}

prefix="/usr/local"
base="%prefix%/heronapi"

for opt in "${@}"; do
  case $opt in
    --prefix=*)
      prefix="$(echo "$opt" | cut -d '=' -f 2-)"
      ;;
    --user)
      base="$HOME/.heronapi"
      ;;
    *)
      usage
      ;;
  esac
done

base="${base//%prefix%/${prefix}}"

function test_write() {
  local file="$1"
  while [ "$file" != "/" ] && [ -n "${file}" ] && [ ! -e "$file" ]; do
    file="$(dirname "${file}")"
  done
  [ -w "${file}" ] || {
    echo >&2
    echo "The Heron installer must have write access to $1!" >&2
    echo >&2
    usage
  }
}

# Test for dependencies
# unzip
if ! which unzip >/dev/null; then
  echo >&2
  echo "unzip not found, please install the corresponding package." >&2
  echo "See http://heron.github.io/docs/install.html for more information on" >&2
  echo "dependencies of Heron." >&2
  exit 1
fi

# Test for dependencies
# tar
if ! which tar >/dev/null; then
  echo >&2
  echo "tar not found, please install the corresponding package." >&2
  echo "See http://heron.github.io/docs/install.html for more information on" >&2
  echo "dependencies of Heron." >&2
  exit 1
fi

# java
if [ -z "${JAVA_HOME-}" ]; then
  case "$(uname -s | tr 'A-Z' 'a-z')" in
    linux)
      JAVA_HOME="$(readlink -f $(which javac) 2>/dev/null | sed 's_/bin/javac__')" || true
      BASHRC="~/.bashrc"
      ;;
    freebsd)
      JAVA_HOME="/usr/local/openjdk8"
      BASHRC="~/.bashrc"
      ;;
    darwin)
      JAVA_HOME="$(/usr/libexec/java_home -v ${JAVA_VERSION}+ 2> /dev/null)" || true
      BASHRC="~/.bash_profile"
      ;;
  esac
fi
if [ ! -x "${JAVA_HOME}/bin/javac" ]; then
  echo >&2
  echo "Java not found, please install the corresponding package" >&2
  echo "See http://heron.github.io/docs/install.html for more information on" >&2
  echo "dependencies of Heron." >&2
  exit 1
fi

# Test for write access
test_write "${base}"

# Do the actual installation
echo -n "Uncompressing."

# Cleaning-up, with some guards.
if [ -d "${base}" -a -x "${base}/lib/heron-api.jar" ]; then
  rm -fr "${base}"
fi

mkdir -p ${base}
echo -n .

unzip -q -o "${BASH_SOURCE[0]}" -d "${base}"
tar xfz "${base}/heron-api.tar.gz" -C "${base}"
echo -n .
chmod -R og-w "${base}"
chmod -R og+rX "${base}"
chmod -R u+rwX "${base}"
echo -n .

rm "${base}/heron-api.tar.gz"

cat <<EOF

Heron API is now installed!

See http://heron.github.io/docs/getting-started.html to start a new project!
EOF
exit 0