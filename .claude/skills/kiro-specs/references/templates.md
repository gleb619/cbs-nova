# Kiro Spec Templates

Annotated templates for all three spec files. Instructions are in `<!-- comments -->`.

---

## `requirements.md` Template

```markdown
# Requirements Document

## Introduction

<!-- 2–4 sentences. What feature is being added? What is its goal?
     Name the integration chain end-to-end (UI → adapter → proxy → backend).
     Name the architecture pattern being followed. -->

The [Feature Name] feature adds [brief description] to [project name] that [what it does].
The goal is to [purpose / what it verifies].

The implementation follows the existing [hexagonal / layered / MVC] architecture:
[component in module A] → [component in module B] → [backend endpoint].

## Glossary

<!-- Define every domain term, class name, and abbreviation used in the document.
     Do NOT include implementation details here — just conceptual definitions. -->

- **[Term]**: [Definition].
- **[Term]**: [Definition].

## Requirements

<!-- Group requirements by concern. Typical groups:
     1. Core domain type/interface
     2. Port/repository interface
     3. Presentational component (if UI)
     4. Adapter / HTTP client
     5. Page / container component (if UI)
     6. Routing
     7. Navigation (if UI)
     8. Full integration chain
     
     Add or remove groups as appropriate for the feature. -->

### Requirement 1: [Domain Type Name]

**User Story:** As a [role], I want [feature], so that [benefit].

#### Acceptance Criteria

<!-- Number as 1.1, 1.2, etc. Use EARS syntax. No file paths or class names. -->

1. THE `[Interface]` SHALL [behavior].
2. THE `[Interface]` SHALL [behavior].
3. THE `[Interface]` SHALL NOT [prohibited behavior].

---

### Requirement 2: [Port / Repository Interface]

**User Story:** As a [role], I want [feature], so that [benefit].

#### Acceptance Criteria

1. THE `[Repository]` interface SHALL declare a method `[method signature]`.
2. THE `[Repository]` interface SHALL be exported from [conceptual location — no full paths].
3. THE `[Repository]` interface SHALL depend only on [allowed dependencies] and no [prohibited dependencies].

---

### Requirement 3: [Presentational Component]

**User Story:** As a [role], I want [feature], so that [benefit].

#### Acceptance Criteria

1. THE `[Component]` SHALL accept a prop named `[prop]` of type `[Type]`.
2. THE `[Component]` SHALL render each item's `[field1]`, `[field2]`, and `[field3]` fields.
3. THE `[Component]` SHALL use [API style], consistent with existing components.
4. THE `[Component]` SHALL be exported from [conceptual location].
5. WHEN the `[prop]` is an empty array, THE `[Component]` SHALL render [empty-state behavior].

---

### Requirement 4: [Adapter]

**User Story:** As a [role], I want [feature], so that [benefit].

#### Acceptance Criteria

1. THE `[Adapter]` SHALL implement the `[Repository]` interface.
2. THE `[Adapter]` SHALL accept a `[HttpClient]` instance via constructor injection.
3. WHEN `[method]()` is called, THE `[Adapter]` SHALL perform a `[METHOD]` request to `[path]`.
4. WHEN the HTTP response is successful, THE `[Adapter]` SHALL return [what it returns].
5. IF the HTTP request fails, THEN THE `[Adapter]` SHALL propagate the error to the caller.
6. THE `[Adapter]` SHALL be located at [conceptual path].

---

### Requirement 5: [Page Component]

**User Story:** As an end user, I want [feature], so that [benefit].

#### Acceptance Criteria

1. THE `[Page]` SHALL fetch [data] on mount using the injected `[Repository]`.
2. THE `[Page]` SHALL delegate rendering to `[PresentationalComponent]`.
3. WHILE [data] is being fetched, THE `[Page]` SHALL display a loading indicator.
4. WHEN [data] is successfully fetched, THE `[Page]` SHALL pass the result to `[Component]` via the `[prop]` prop.
5. IF the fetch fails, THEN THE `[Page]` SHALL display an error message.
6. THE `[Page]` SHALL use [API style], consistent with `[AnalogousComponent]`.
7. THE `[Page]` SHALL be located at [conceptual path].

---

### Requirement 6: [Route]

**User Story:** As a frontend developer, I want [feature], so that [benefit].

#### Acceptance Criteria

1. THE `[Router]` SHALL register a route with path `[path]` and name `[name]` pointing to `[Component]`.
2. THE `[Router]` SHALL export a `[routesFn]` function returning `RouteRecordRaw[]`, consistent with existing patterns.
3. THE `[Router]` SHALL be located at [conceptual path].
4. THE main router file SHALL import and spread `[routesFn]()` into the routes array.

---

### Requirement 7: [Navigation]

**User Story:** As an end user, I want [feature], so that [benefit].

#### Acceptance Criteria

1. THE `[Component]` SHALL include a navigation link with the text "[label]" pointing to the `[path]` route.
2. THE navigation link SHALL use a `<router-link>` element with `to="[path]"`.
3. THE `[Component]` SHALL remain otherwise unchanged in structure and style.

---

### Requirement 8: Full Integration Chain

**User Story:** As a developer, I want [feature] to successfully retrieve and display data from the backend, so that the
full integration chain is verified.

#### Acceptance Criteria

1. WHEN the user navigates to `[path]`, THE `[Page]` SHALL fetch [data] from `[endpoint]` via [proxy/gateway].
2. WHEN `[endpoint]` returns a non-empty list, THE `[Page]` SHALL display at least one [item] with a non-empty `[field]`
   field.
3. WHEN `[endpoint]` returns HTTP 200, THE `[Adapter]` SHALL return a `[Type][]` array with the same length as the
   response body array.
4. IF `[endpoint]` returns HTTP 500, THEN THE `[Page]` SHALL display an error message to the user.
```

---

## `design.md` Template

```markdown
# Design Document: [Feature Name]

## Overview

<!-- One paragraph. What does this feature add and to which project?
     Then show the full integration chain as an inline diagram. -->

The [Feature Name] feature adds [description] to `[project]` that [what it does].
It validates the full integration chain:

```

[ComponentA] → [ComponentB] → [Adapter] → [Proxy] → [BackendController] → [Service]

```

The implementation follows the project's [architecture] architecture:
- **`[module-a]`**: owns [what this module owns].
- **`[module-b]`**: owns [what this module owns].

## Architecture

<!-- Mermaid diagram showing all components and their relationships.
     Use subgraphs to group by module. Show implements/uses/calls arrows. -->

```mermaid
graph TD
  subgraph [module-a]
    TypeA["[Type].ts (interface)"]
    RepoPort["[Repository].ts (port)"]
    ListComponent["[ListComponent].vue (presentational)"]
  end

  subgraph [module-b] / src/app/[feature]
    Adapter["infrastructure/secondary/[Adapter].ts (adapter)"]
    PageComponent["infrastructure/primary/[PageComponent].vue (page)"]
    Router["application/[Router].ts (route)"]
  end

  subgraph [module-b] / src/app
    AppRouter["router.ts"]
    HomePage["home/.../[HomePage].vue"]
  end

  subgraph backend
    Controller["[METHOD] [endpoint]"]
  end

  Adapter -->|implements| RepoPort
  Adapter -->|uses| HttpClient
  Adapter -->|[METHOD] [endpoint]| Controller
  PageComponent -->|injects| RepoPort
  PageComponent -->|renders| ListComponent
  Router -->|registers| PageComponent
  AppRouter -->|spreads| Router
  HomePage -->|router-link to [path]| Router
```

<!-- Add a note about any proxy/gateway that sits between frontend and backend. -->

## Components and Interfaces

<!-- One section per component. Show the TypeScript interface or class signature.
     Explain the design decision / why it was designed this way. -->

### `[module-a]/[path]/[Type].ts`

<!-- What does this interface represent? -->

```ts
export interface [Type] {
  [field]: [tsType];
  [field]: [tsType];
}
```

### `[module-a]/[path]/[Repository].ts`

<!-- Port interface — what is it decoupling? -->

```ts
import type { [Type] } from './[Type]';

export interface [Repository] {
  [method](): Promise<[Type][]>;
}
```

### `[module-a]/[path]/[ListComponent].vue`

<!-- Presentational component. What props does it accept? What does it render?
     When does it show the empty state? -->

### `[module-b]/[path]/[Adapter].ts`

<!-- HTTP adapter. How does it implement the port? Show constructor injection. -->

```ts
export class [Adapter] implements [Repository] {
  constructor(private readonly http: [HttpClient]) {}

  async [method](): Promise<[Type][]> {
    const response = await this.http.get<[Type][]>('[endpoint]');
    return response.data;
  }
}
```

### `[module-b]/[path]/[PageComponent].vue`

<!-- Page component. What state does it manage? What lifecycle hook triggers the fetch? -->

### `[module-b]/[path]/[Router].ts`

```ts
export const [routesFn] = (): RouteRecordRaw[] => [
  { path: '[path]', name: '[Name]', component: [PageComponent] },
];
```

### `[main router file]`

```ts
export const routes = [...[existingRoutesFn](), ...[routesFn]()];
```

### `[NavigationComponent].vue`

<!-- What minimal change is made? What must remain unchanged? -->

## Data Models

### `[Type]` (TypeScript)

| Field     | Type       | Source (`[BackendDto]`) |
|-----------|------------|-------------------------|
| `[field]` | `[tsType]` | `[javaType] [field]`    |
| `[field]` | `[tsType]` | `[javaType] [field]`    |

### HTTP Contract

`[METHOD] [endpoint]` → `200 OK` → `[Dto][]` (JSON array)

<!-- Describe any proxy rewriting that happens. -->

### Dependency Injection

<!-- How is the adapter wired to the port? Which file? Which DI mechanism? -->

## Correctness Properties

<!-- Only include this section if property-based testing is used.
     
     A property is a characteristic or behavior that should hold true across all valid
     executions — essentially a formal statement of what the system should do.
     Properties bridge human-readable specs and machine-verifiable guarantees. -->

### Property 1: [Adapter] mapping correctness

*For any* array of `[Dto]`-shaped objects returned by the HTTP layer, `[Adapter].[method]()` shall return a `[Type][]`
of the same length where each element's fields match the corresponding response item exactly.

**Validates: Requirements [N.M], [N.M]**

### Property 2: [ListComponent] renders all items

*For any* non-empty array of `[Type]` objects passed as the `[prop]` prop, `[ListComponent]` shall render exactly as
many rows as there are items, and each row shall contain the item's relevant fields.

**Validates: Requirements [N.M]**

### Property 3: [ListComponent] empty-state

*For* an empty `[prop]` array, `[ListComponent]` shall render an empty-state message and zero data rows.

**Validates: Requirements [N.M]**

### Property 4: [PageComponent] passes fetched data to [ListComponent]

*For any* array of `[Type]` objects resolved by the injected `[Repository]`, `[PageComponent]` shall pass that exact
array as the `[prop]` prop to `[ListComponent]`.

**Validates: Requirements [N.M]**

## Error Handling

| Scenario                  | Component         | Behaviour                                                       |
|---------------------------|-------------------|-----------------------------------------------------------------|
| `[endpoint]` returns 5xx  | `[Adapter]`       | Propagates the error (rejects the promise)                      |
| Promise rejected on mount | `[PageComponent]` | Catches in `mounted`, sets `error` state, renders error message |
| Empty [data] list         | `[ListComponent]` | Renders empty-state message (no error)                          |
| Network timeout           | `[Adapter]`       | HTTP client throws; `[PageComponent]` catches and shows error   |

<!-- `[Adapter]` does NOT swallow errors — it lets them propagate so the page layer decides how to present them. -->

## Testing Strategy

### Unit Tests ([TestFramework] + [MountingLibrary])

<!-- List what each component's unit tests verify. Be specific about what is stubbed. -->

- `[Adapter]` — stub `[HttpClient]`; assert `[METHOD] [endpoint]` is called and data is returned; assert error
  propagation on rejection.
- `[PageComponent]` — stub `[Repository]`; assert loading state before resolution, [data] passed to `[ListComponent]`
  after resolution, error message on rejection.
- `[ListComponent]` — mount with a fixed array; assert rows rendered; mount with `[]`; assert empty-state message.
- `[Router]` — call `[routesFn]()`; assert the returned array contains `{ path: '[path]', name: '[Name]' }`.
- `[NavigationComponent]` — assert a `router-link` with `to="[path]"` and text "[label]" is present.

### Property-Based Tests ([PropertyLibrary])

<!-- Only include if property-based testing is used.
     Each property test must run a minimum of 100 iterations.
     Tag each test with a comment referencing the feature and property number. -->

**Property 1 — [Adapter] mapping correctness**

```
// Feature: [feature-name], Property 1: [Adapter] mapping correctness
fc.assert(fc.asyncProperty(fc.array([dtoArbitrary]()), async (dtos) => {
  // stub [HttpClient] to resolve with { data: dtos }
  const result = await [adapter].[method]();
  expect(result).toHaveLength(dtos.length);
  result.forEach((item, i) => {
    // assert each field matches
  });
}), { numRuns: 100 });
```

**Property 2 — [ListComponent] renders all items**

```
// Feature: [feature-name], Property 2: [ListComponent] renders all items
fc.assert(fc.property(fc.array([typeArbitrary](), { minLength: 1 }), (items) => {
  const wrapper = mount([ListComponent], { props: { [prop]: items } });
  expect(wrapper.findAll('[data-testid="[row-testid]"]')).toHaveLength(items.length);
}), { numRuns: 100 });
```

**Property 3 — [ListComponent] empty-state**

```
// Feature: [feature-name], Property 3: [ListComponent] empty-state
const wrapper = mount([ListComponent], { props: { [prop]: [] } });
expect(wrapper.find('[data-testid="[empty-testid]"]').exists()).toBe(true);
expect(wrapper.findAll('[data-testid="[row-testid]"]')).toHaveLength(0);
```

**Property 4 — [PageComponent] passes fetched data to [ListComponent]**

```
// Feature: [feature-name], Property 4: [PageComponent] passes fetched data to [ListComponent]
fc.assert(fc.asyncProperty(fc.array([typeArbitrary]()), async (items) => {
  // stub [Repository].[method] to resolve with items
  const wrapper = mount([PageComponent], ...);
  await flushPromises();
  expect(wrapper.findComponent([ListComponent]).props('[prop]')).toEqual(items);
}), { numRuns: 100 });
```

<!-- Note where property tests live vs unit tests (often different modules). -->

```

---

## `tasks.md` Template

```markdown
# Implementation Plan: [Feature Name]

## Overview

<!-- 2–4 sentences.
     State the architecture pattern followed.
     Name which module owns domain/port/presentational pieces.
     Name which module owns adapter/page/router pieces.
     Mention what test types are used and where they live. -->

Implement the [feature name] feature following the project's [architecture] architecture.
[Module A] owns [what it owns]; [Module B] owns [what it owns].
[Test type A] ([library]) cover [what they cover] in [module]; [test type B] cover [module]-specific files.

## Tasks

<!-- Order tasks by dependency:
     1. Domain types (interfaces, models)
     2. Port interfaces
     3. Presentational components
     4. [Checkpoint]
     5. Adapters (HTTP, etc.)
     6. Page components
     7. Routing
     8. Navigation / wiring
     9. DI wiring
     10. [Final Checkpoint]
     
     Optional tasks (tests, etc.) are marked with `*` after the checkbox: `- [ ]*`
     Completed tasks use [x]. New specs start with [ ]. -->

- [ ] 1. Create `[Type]` interface and `[Repository]` port in `[module-a]`
  - Create `[path]/[Type].ts` exporting the `[Type]` interface with fields [list fields]
  - Create `[path]/[Repository].ts` exporting the `[Repository]` interface with `[method](): Promise<[Type][]>`; import only from `./[Type]`
  - _Requirements: 1.1, 1.2, 1.3, 2.1, 2.2, 2.3_

- [ ] 2. Implement `[ListComponent]` presentational component in `[module-a]`
  - [ ] 2.1 Create `[path]/[ListComponent].vue` using [API style]
    - Accept `[prop]: [Type][]` as a prop
    - Render each item's [fields] in a row with `data-testid="[row-testid]"`
    - Render an empty-state element with `data-testid="[empty-testid]"` when the array is empty
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

  - [ ]* 2.2 Write property test for `[ListComponent]` renders all items (Property 2)
    - File: `[test-path]/[ListComponent].spec.ts`
    - **Property 2: [ListComponent] renders all items**
    - **Validates: Requirements 3.2**
    - Use `fc.array([typeArbitrary](), { minLength: 1 })` with `numRuns: 100`; assert row count equals items length

  - [ ]* 2.3 Write property test for `[ListComponent]` empty-state (Property 3)
    - File: `[test-path]/[ListComponent].spec.ts` (same file, additional test)
    - **Property 3: [ListComponent] empty-state**
    - **Validates: Requirements 3.5**
    - Mount with `[prop]: []`; assert `[data-testid="[empty-testid]"]` exists and row count is 0

- [ ] 3. Implement `[Adapter]` in `[module-b]`
  - [ ] 3.1 Create `[path]/[Adapter].ts`
    - Implement `[Repository]`; accept `[HttpClient]` via constructor injection
    - `[method]()` performs `[METHOD] [endpoint]` and returns `response.data` as `[Type][]`; propagates errors without swallowing
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6_

  - [ ]* 3.2 Write property test for `[Adapter]` mapping correctness (Property 1)
    - File: `[test-path]/[Adapter].spec.ts`
    - **Property 1: [Adapter] mapping correctness**
    - **Validates: Requirements 4.4, 8.3**
    - Use `fc.array([dtoArbitrary]())` with `numRuns: 100`; stub `[HttpClient]` to resolve `{ data: dtos }`; assert result length and each field matches

  - [ ]* 3.3 Write unit tests for `[Adapter]`
    - File: `[unit-test-path]/[Adapter].spec.ts`
    - Stub `[HttpClient]`; assert `[METHOD] [endpoint]` is called and data is returned
    - Assert error propagation when `[HttpClient].[method]` rejects
    - _Requirements: 4.3, 4.4, 4.5_

- [ ] 4. Checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 5. Implement `[PageComponent]` page component in `[module-b]`
  - [ ] 5.1 Create `[path]/[PageComponent].vue` using [API style] (mirrors `[AnalogousComponent]` style)
    - On `mounted`, call injected `[Repository].[method]()`; manage `loading`, `[data]`, and `error` state
    - While loading, display a loading indicator
    - On success, pass `[data]` to `[ListComponent]` via the `[prop]` prop
    - On rejection, display an error message
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 8.1, 8.2, 8.4_

  - [ ]* 5.2 Write property test for `[PageComponent]` passes fetched data to `[ListComponent]` (Property 4)
    - File: `[test-path]/[PageComponent].spec.ts`
    - **Property 4: [PageComponent] passes fetched data to [ListComponent]**
    - **Validates: Requirements 5.4**
    - Use `fc.array([typeArbitrary]())` with `numRuns: 100`; stub `[Repository].[method]` to resolve with the generated array; after `flushPromises()`, assert `[ListComponent]` receives that exact array as `[prop]` prop

  - [ ]* 5.3 Write unit tests for `[PageComponent]`
    - File: `[unit-test-path]/[PageComponent].spec.ts`
    - Stub `[Repository]`; assert loading indicator is shown before resolution
    - Assert [data] are passed to `[ListComponent]` after successful resolution
    - Assert error message is displayed when `[method]` rejects
    - _Requirements: 5.3, 5.4, 5.5_

- [ ] 6. Implement `[Router]` and wire into main router
  - [ ] 6.1 Create `[path]/[Router].ts`
    - Export `[routesFn](): RouteRecordRaw[]` returning `[{ path: '[path]', name: '[Name]', component: [PageComponent] }]`
    - _Requirements: 6.1, 6.2, 6.3_

  - [ ] 6.2 Update `[main router file]` to import and spread `[routesFn]()`
    - Add import for `[routesFn]`
    - Change `routes` to `[...[existingRoutesFn](), ...[routesFn]()]`
    - _Requirements: 6.4_

  - [ ]* 6.3 Write unit tests for `[Router]`
    - File: `[unit-test-path]/[Router].spec.ts`
    - Call `[routesFn]()`; assert the array contains `{ path: '[path]', name: '[Name]' }` with a defined component
    - _Requirements: 6.1, 6.2_

- [ ] 7. Add [feature] navigation link to `[NavigationComponent]`
  - [ ] 7.1 Update `[path]/[NavigationComponent].vue`
    - Add `<router-link to="[path]">[Label]</router-link>` without altering any other structure or styles
    - _Requirements: 7.1, 7.2, 7.3_

  - [ ]* 7.2 Write unit tests for `[NavigationComponent]`
    - File: `[unit-test-path]/[NavigationComponent].spec.ts`
    - Assert a `router-link` with `to="[path]"` and text "[Label]" is present
    - _Requirements: 7.1, 7.2_

- [ ] 8. Wire `[Adapter]` into dependency injection
  - Update `[DI wiring file]` to provide `[Adapter]` bound to the `[Repository]` injection key, following the same pattern used for other adapters
  - _Requirements: 5.1, 8.1_

- [ ] 9. Final checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

<!-- Customize per project. Standard notes: -->

- Tasks marked with `*` are optional and can be skipped for a faster MVP
- Property tests live in `[module-a]/[test-path]/` where `[property-library]` is already a dev dependency
- Unit tests for `[module-b]`-specific files live in `[module-b]/[test-path]/` following the existing structure
- Each property test must include a comment: `// Feature: [feature-name], Property N: <title>`
- `data-testid="[row-testid]"` and `data-testid="[empty-testid]"` attributes are required on `[ListComponent]` for property tests to work
```