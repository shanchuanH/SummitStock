# Main branch protection

The `main` branch is a release boundary. Configure the repository ruleset so that changes reach it only through a pull request after the `verify` job in `.github/workflows/ci.yml` succeeds.

Required settings:

- require a pull request before merging;
- require at least one approving review and dismiss stale approvals;
- require the `verify` status check to pass and require branches to be up to date;
- require conversation resolution;
- block force pushes and deletion;
- apply the ruleset to administrators and automation unless an audited break-glass exception is used.

After changing the ruleset, verify it from a non-`main` branch by opening a pull request with a deliberately failing check. Confirm that GitHub blocks the merge, restore the check, and confirm that the merge becomes eligible only after `verify` succeeds.

Repository rules are external GitHub state and cannot be established by application code. The release operator records the ruleset URL and the verification date in the release evidence.
