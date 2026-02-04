#!/usr/bin/env bash
set -euo pipefail

ant clean
ant compile
ant javadocs
ant dist
ant srcdist
ant functional-tests
ant war

