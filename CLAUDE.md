# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

ctdynamo ("compile time dynamo") is a replacement for the AWS SDK v2 `dynamodb-enhanced` package. Instead of
reflecting over annotated beans at runtime, an annotation processor generates a concrete table/codec class for each
annotated bean at compile time, so encode/decode is straight-line generated code with no reflection.

Published as `ai.phast:ctdynamo-{runtime,processor,mocks}`. Consumers take `ctdynamo-runtime` as a normal dependency
and `ctdynamo-processor` as a compile-time dependency (the processor registers itself via `@AutoService`, so simply
being on the compile classpath activates it).

## Build and test

```bash
mvn clean install                      # build all three modules
mvn -pl mocks -am test                 # build deps and test one module
mvn -pl mocks test -Dtest=UpdateTest   # single test class
mvn -pl mocks test -Dtest=UpdateTest#testUpdate_shouldAddItem_whenNotPresent   # single test method
./install.sh                           # mvn clean install, then install each module jar under the explicit version
./deploy.sh                            # deploy into the file-based maven repo at ~/src/mvn
```

Checkstyle is bound to the `validate` phase in every module with `failsOnError`, so it runs on every build — a
checkstyle violation breaks the build before compilation. Config is `checkstyle.xml` at the repo root.

JaCoCo report (`runtime` and `mocks`) is attached to the `package` phase.

Source/target is Java 11 in all modules, but the code uses `var` freely. Local JDK is 17.

## Module layout and dependency chain

`runtime` ← `processor` ← `mocks`. Each depends on the previous at compile scope, so a change in `runtime` requires
rebuilding all three.

- **runtime** — the annotations (`ai.phast.ctdynamo.annotations`) and the base classes the generated code extends
  (`DynamoIndex`, `DynamoTable`, `Query`, `Scan`, `Transaction`, `ConditionExpression`, …). Only depends on the AWS SDK.
- **processor** — the `javax.annotation.processing` implementation. `DynamoProcessor` drives rounds;
  `CtClassGenerator` (~1500 lines, the bulk of the work) emits source via JavaPoet.
- **mocks** — `DynamoMockUtil` builds in-memory tables for unit tests, backed by `MockDynamoClient` (an implementation
  of `DynamoDbClient`) and `InMemoryDynamoStore`. Also contains an ANTLR grammar (`Dynamo.g4`) plus
  `ExpressionEvaluator` to actually evaluate DynamoDB condition/update expressions in memory.

## Annotation processing flow

`@DynamoItem` on a class triggers generation. `Output.TABLE` (default) generates `<Name>DynamoTable`;
`Output.CODEC` generates `<Name>DynamoCodec` for beans nested inside table items; both can be requested.

**The deferral trick** (`DynamoProcessor.buildDeferralClass`): annotation processor ordering is not guaranteed, and
Lombok-generated getters/setters must exist before ctdynamo inspects the class. So when `defer=true` (the default),
the first round emits a throwaway `<Name>DynamoDefer` class carrying a copy of the `@DynamoItem` annotation plus
`deferredFrom=<Name>.class`. In a later round the processor sees that deferral class, follows `deferredFrom` back to
the original, and generates against the now-complete class. `<Name>DynamoDefer` classes in generated sources are
expected noise, not a bug. Setting `defer=false` skips this when no class-modifying processors are in play.

Key annotations: `@DynamoPartitionKey` / `@DynamoSortKey` for the table key, `@DynamoSecondaryPartitionKey({"idx"})` /
`@DynamoSecondarySortKey({"idx"})` for GSI/LSI membership (an index may have no sort key), `@DynamoAttribute` for
attribute renaming and custom `DynamoCodec`, `@DynamoIgnore`, `@DynamoStringSet`.

An index may have up to four partition keys. `@DynamoSecondaryPartitionKey(value = "idx", order = 2)` gives the
1-based position. Position comes from `order`, never from declaration order, and the orders for an index must start
at 1 with no gaps — the processor rejects gaps and out-of-range orders at compile time.

`order` is an `int[]` paired with `value()` by position, so an attribute can sit at a different position in each
index it belongs to: `@DynamoSecondaryPartitionKey(value = {"a", "b"}, order = {1, 2})`. When present it must have
exactly one entry per index name (also enforced at compile time); the default empty array means position 1
everywhere. Java's single-element shorthand means `order = 2` still works for an attribute in one index.

Index naming: `@DynamoItem(indexNames = {"serverIndexKey", "javaName"})` maps the DynamoDB index name to the name
used in generated Java. Generated accessors are `get<Name>Index()`, returning the generated index class itself — a
`public static final` nested class of the table (`<Item>DynamoTable.<Name>Index`) whose constructor is private, so
only the enclosing table can build one. Returning the concrete class rather than the `DynamoIndex` supertype is what
lets callers name an index type without writing out its six type arguments, and is what makes the generated `query`
overload below visible. `getIndex(name, ...)` looks one up by name and takes all five key classes, any of which may
be null to skip the type check; it returns the `DynamoIndex` base, so that path has no typed `query` overload and
must go through `query().partitionValue(...)`.

## Runtime design

- `DynamoIndex<T, Partition1T, Partition2T, Partition3T, Partition4T, SortT>` is the base; `DynamoTable<T, PartitionT,
  SortT>` extends it as `DynamoIndex<T, PartitionT, Void, Void, Void, SortT>`, adding writes — a table always has
  exactly one partition key. Unused partition slots and a missing sort key are `Void`. The generated
  `getPartitionValueN` for an unused slot returns null, which is how the runtime detects how many partition keys an
  index actually has; `DynamoTable` implements slots 2-4 as final, so generated table classes must not re-emit them.
  Each key getter comes as a pair of overloads: `getPartitionValueN(T item)` reads the key off an item (declared on
  `DynamoIndex`, all four slots), while `getPartitionValue1(AttributeValue)` decodes one (declared on `DynamoTable`
  only — indexes decode via `decodeExclusiveStart`). `getSortValue` follows the same item/AttributeValue pattern.
- Every index/table holds *both* a `DynamoDbClient` and a `DynamoDbAsyncClient`, at least one non-null. Sync methods
  call `getAsyncClient().foo(req).join()` when only the async client is present. New operations should preserve this
  pattern.
- Queries and scans are fluent builders that end in `invoke()` rather than `build()` (`Query`, `Scan`, extending
  `BaseQueryScan`), returning paged/iterable results (`PagedResult`, `IterableResult`). The sync form requests the
  next page once the iterator/stream is exhausted; the async form prefetches it as soon as you begin reading the
  current page.
- Partition values are set by a `query`/`queryAsync` overload that takes exactly as many as the caller's index has,
  so nobody ever passes a `Void`. `DynamoIndex` declares only the no-arg `query()`/`queryAsync()` — it cannot know
  how many partition keys a subclass has. `DynamoTable` adds `query(partitionValue)`, since a table always has
  exactly one, and the processor emits `query(p1..pN)`/`queryAsync(p1..pN)` into each generated index class. Both
  delegate to `Query.partitionValue`, which still takes all four and reads a null as "this index has fewer".
  A sort key is chained afterwards (`sortEquals`, `sortBetween`, `sortGreaterThan`, …), never passed to `query`.
- Build a `Key` with the static `Key.of(...)` factories, not `new Key<>(...)`: the diamond form cannot infer the
  unused partition types, so it fails wherever a `Key<P, Void, Void, Void, S>` is expected.
- `updateItem` uses a map of attribute name → expression string, with three sentinel expressions on `DynamoTable`:
  `UPDATE_IGNORE_ATTRIBUTE` (`"-"`), `UPDATE_REMOVE_ATTRIBUTE` (`""`), and `ADD_PREFIX` (`"+"`).
- Internal expression placeholders are namespaced `:ctdynamo_*` and `#<attributeName>` — partition values are
  `:ctdynamo_p1` through `:ctdynamo_p4`, sort bounds `:ctdynamo_s1`/`:ctdynamo_s2`. `MockDynamoClient` and
  `InMemoryDynamoStore` pattern-match on these generated forms, so changing the generated expression syntax in
  `runtime` can silently break the mocks. `InMemoryDynamoStore` keys each partition on the whole tuple of partition
  values.

## Testing conventions

There is no separate test module for the processor. `mocks/src/test/java/.../tables/` and `.../processorTest/` hold
annotated fixture beans; compiling the `mocks` test sources runs the processor over them into
`mocks/target/generated-test-sources/test-annotations/`, and the tests exercise the generated code against the mock
client. **To test a processor change, run the `mocks` tests** — and read the generated output in that directory when
debugging codegen.

`runtime` has its own tests using hand-written `MockClient`/`MockTable`/`MockItem` stand-ins, independent of the
`mocks` module (which cannot be a dependency of `runtime` — it's downstream).

Test methods are named `testSubject_shouldOutcome_whenCondition`, with `// Setup` / `// Act` / `// Verify` comment
blocks. JUnit 5 with `Assertions.*` calls (static imports are banned by checkstyle).

## Style rules enforced by checkstyle

Worth knowing before writing code, since violations fail the build:

- Javadoc required on **all** types, methods, and fields down to `private` scope, with `@param`/`@return`/`@throws`
  (validated against actual throws). Paragraphs after the first need `<p>`.
- No star imports, no static imports, no unused imports.
- `DeclarationOrder` and `OverloadMethodsDeclarationOrder`: fields before constructors before methods, and overloads
  adjacent.
- `HiddenField` (constructor params and setters exempt), `InnerTypeLast`, no tabs.
- `AbbreviationAsWordInName` allows at most 2 consecutive capitals.

## Versioning

The version appears in five places that must be kept in sync: the parent `pom.xml`, the `<parent>` block of each of
the three module poms, and the `version=` line in `install.sh`. If the module `<parent>` versions drift from the
aggregator's, Maven resolves the parent from `~/.m2` rather than from disk, which used to break checkstyle's
`configLocation`; that is now anchored on `${maven.multiModuleProjectDirectory}` instead, which is why the otherwise
empty `.mvn/` directory at the repo root must not be deleted (see `.mvn/README.md`).

Commit messages are prefixed with the Jira key, e.g. `[DCS-4572] Allow sort-less GSIs and LSIs`.
