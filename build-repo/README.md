# build-repo

A vendored Maven repository containing the `org.gbif.*` / `au.org.ala.*` artifacts
(plus a few third-party forks hosted only there) that this build needs. GBIF regularly
deletes old versions from `repository.gbif.org`, which used to break CI; these copies
keep the build reproducible.

Wiring (root `pom.xml`):
- `<repositories>` / `<pluginRepositories>` include `project-bundled` at
  `file://${maven.multiModuleProjectDirectory}/build-repo` (`.mvn/` at the repo root
  makes that property resolve to the repo root from any subdirectory).
- the root `<parent>` (`org.gbif:motherpom:59`) is resolved from the copy in here via
  `relativePath`, because Maven cannot interpolate the repository URL during parent
  resolution.

Only `.jar`, `.pom` and checksums are stored. Do not hand-edit.

## Adding / updating an artifact

Copy it in Maven layout, e.g.:

```
build-repo/org/gbif/foo/1.2.3/foo-1.2.3.jar
build-repo/org/gbif/foo/1.2.3/foo-1.2.3.pom
build-repo/org/gbif/foo/1.2.3/foo-1.2.3.jar.sha1
build-repo/org/gbif/foo/1.2.3/foo-1.2.3.pom.sha1
```

Or regenerate the whole repo: do a clean build into a fresh local repository
(`mvn ... -Dmaven.repo.local=/tmp/cleanrepo`), then copy every artifact under
`org/gbif` (excluding `org/gbif/pipelines`, which is built here), `au/org/ala`, and
any artifact whose `_remote.repositories` names a `repository.gbif.org` repository.
