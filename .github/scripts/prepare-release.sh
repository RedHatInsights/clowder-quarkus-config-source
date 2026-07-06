#!/usr/bin/env bash
# Computes the next version from conventional commits since the last tag and
# opens a release PR, or recognizes that HEAD already *is* a release commit.
#
# Commits are GPG-signed using a bot identity whose public key is registered
# on GitHub, via RELEASE_BOT_GPG_PRIVATE_KEY/RELEASE_BOT_GPG_PASSPHRASE and
# RELEASE_BOT_NAME/RELEASE_BOT_EMAIL (which must match a verified name/email
# on that account). Because the signature is real, it satisfies the org's
# "required_signatures" ruleset on any branch, unlike commits created through
# GitHub's REST/Git-Data API by a plain GITHUB_TOKEN.
set -euo pipefail

LAST_SUBJECT=$(git log -1 --pretty=%s)
if [[ "${LAST_SUBJECT}" =~ ^chore\(main\):\ release\ (.+)$ ]]; then
  echo "HEAD is already a release commit for ${BASH_REMATCH[1]}, nothing to prepare."
  echo "release_created=true" >>"${GITHUB_OUTPUT}"
  echo "version=${BASH_REMATCH[1]}" >>"${GITHUB_OUTPUT}"
  exit 0
fi

LATEST_TAG=$(git tag -l 'v*' --sort=-v:refname | head -n1)
if [ -z "${LATEST_TAG}" ]; then
  echo "No previous v* tag found, cannot compute a base version." >&2
  echo "release_created=false" >>"${GITHUB_OUTPUT}"
  exit 0
fi

RANGE="${LATEST_TAG}..HEAD"
SUBJECTS=$(git log "${RANGE}" --pretty=format:'%s')
BODIES=$(git log "${RANGE}" --pretty=format:'%b')

BUMP=none
if echo "${SUBJECTS}" | grep -qE '^[a-zA-Z]+(\([^)]*\))?!:' || echo "${BODIES}" | grep -q 'BREAKING CHANGE:'; then
  BUMP=major
elif echo "${SUBJECTS}" | grep -qE '^feat(\([^)]*\))?:'; then
  BUMP=minor
elif echo "${SUBJECTS}" | grep -qE '^fix(\([^)]*\))?:'; then
  BUMP=patch
fi

if [ "${BUMP}" = none ]; then
  echo "No releasable (feat/fix/breaking) commits since ${LATEST_TAG}."
  echo "release_created=false" >>"${GITHUB_OUTPUT}"
  exit 0
fi

VERSION="${LATEST_TAG#v}"
IFS='.' read -r MAJOR MINOR PATCH <<<"${VERSION%%-*}"
case "${BUMP}" in
  major) MAJOR=$((MAJOR + 1)); MINOR=0; PATCH=0 ;;
  minor) MINOR=$((MINOR + 1)); PATCH=0 ;;
  patch) PATCH=$((PATCH + 1)) ;;
esac
NEW_VERSION="${MAJOR}.${MINOR}.${PATCH}"
echo "Bumping ${LATEST_TAG} -> v${NEW_VERSION} (${BUMP})"

./mvnw -q -B versions:set-property -Dproperty=revision -DnewVersion="${NEW_VERSION}" -DgenerateBackupPoms=false

FEATURES=$(git log "${RANGE}" -E --grep='^feat(\([^)]*\))?:' --pretty=format:"* %s ([%h](https://github.com/${GITHUB_REPOSITORY}/commit/%H))")
FIXES=$(git log "${RANGE}" -E --grep='^fix(\([^)]*\))?:' --pretty=format:"* %s ([%h](https://github.com/${GITHUB_REPOSITORY}/commit/%H))")

{
  echo "# Changelog"
  echo
  echo "## [${NEW_VERSION}](https://github.com/${GITHUB_REPOSITORY}/compare/${LATEST_TAG}...v${NEW_VERSION})"
  if [ -n "${FEATURES}" ]; then
    echo
    echo "### Features"
    echo
    echo "${FEATURES}"
  fi
  if [ -n "${FIXES}" ]; then
    echo
    echo "### Bug Fixes"
    echo
    echo "${FIXES}"
  fi
  echo
  tail -n +2 CHANGELOG.md
} >CHANGELOG.md.new
mv CHANGELOG.md.new CHANGELOG.md

for var in RELEASE_BOT_GPG_PRIVATE_KEY RELEASE_BOT_GPG_PASSPHRASE RELEASE_BOT_NAME RELEASE_BOT_EMAIL; do
  if [ -z "${!var:-}" ]; then
    echo "Error: ${var} secret is not configured." >&2
    exit 1
  fi
done

if ! printenv RELEASE_BOT_GPG_PRIVATE_KEY | gpg --batch --import; then
  echo "Error: failed to import RELEASE_BOT_GPG_PRIVATE_KEY. Check it's a valid ASCII-armored key." >&2
  exit 1
fi
KEY_FINGERPRINT=$(gpg --list-secret-keys --with-colons --fingerprint 2>/dev/null | awk -F: '/^fpr/{print $10; exit}')
if [ -z "${KEY_FINGERPRINT}" ]; then
  echo "Error: no GPG secret key found after import." >&2
  exit 1
fi

echo "allow-preset-passphrase" >>~/.gnupg/gpg-agent.conf
gpg-connect-agent reloadagent /bye
KEYGRIP=$(gpg --list-secret-keys --with-keygrip --with-colons 2>/dev/null | awk -F: '/^grp/{print $10; exit}')
if ! /usr/lib/gnupg/gpg-preset-passphrase --preset "${KEYGRIP}" <<<"${RELEASE_BOT_GPG_PASSPHRASE}"; then
  echo "Error: failed to preset GPG passphrase." >&2
  exit 1
fi

git config user.signingkey "${KEY_FINGERPRINT}"
git config commit.gpgsign true
git config user.name "${RELEASE_BOT_NAME}"
git config user.email "${RELEASE_BOT_EMAIL}"

BRANCH="release/v${NEW_VERSION}"
git checkout -b "${BRANCH}"
git add pom.xml CHANGELOG.md
git commit -S -m "chore(main): release ${NEW_VERSION}"
git push origin "${BRANCH}"

gh label create "autorelease: pending" --color BFD4F2 --description "Automated release PR, ready to auto-merge" --force

PR_URL=$(gh pr create \
  --title "chore(main): release ${NEW_VERSION}" \
  --body "Automated release PR for v${NEW_VERSION}. Auto-merges once opened." \
  --base main \
  --head "${BRANCH}" \
  --label "autorelease: pending")

gpg --batch --yes --delete-secret-and-public-key "${KEY_FINGERPRINT}" || true
gpgconf --kill gpg-agent || true
rm -rf ~/.gnupg
git config --unset user.signingkey || true
git config --unset commit.gpgsign || true

echo "release_created=false" >>"${GITHUB_OUTPUT}"
echo "pr_url=${PR_URL}" >>"${GITHUB_OUTPUT}"
