# Why this directory exists

Do not delete it, even though it holds no Maven configuration.

The `mvn` launcher sets `maven.multiModuleProjectDirectory` by walking up from the directory it was invoked in (or
from the `-f` argument) until it finds a `.mvn` directory. The three module poms point checkstyle at
`${maven.multiModuleProjectDirectory}/checkstyle.xml`, so without this anchor that property would resolve to the
module's own directory and `mvn validate` from inside `runtime/`, `processor/` or `mocks/` would fail with
"Unable to find configuration file at location: .../runtime/checkstyle.xml".

This replaced `${project.parent.basedir}`, which silently broke whenever the module poms' `<parent>` version drifted
from the aggregator's: Maven then resolved the parent from `~/.m2` instead of from disk, leaving `basedir` undefined.
