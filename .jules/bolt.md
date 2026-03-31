## Testing learnings
When testing coroutine based UseCases using mocking with `mockk` library, prefer `coEvery` and `coVerify` rather than standard `every` and `verify`.

The project requires `google-services.json` inside the `/app/` directory to run full unit tests `./gradlew testDebugUnitTest`. Without it `processDebugGoogleServices` task fails. We bypassed this by providing a dummy `google-services.json`.

Use `@BeforeEach` instead of `@Before` to execute initializations.
