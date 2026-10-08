---
name: Institutional Ledger & Core Banking
colors:
  surface: '#fbf9fa'
  surface-dim: '#dbd9db'
  surface-bright: '#fbf9fa'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f5f3f5'
  surface-container: '#f0edef'
  surface-container-high: '#eae7e9'
  surface-container-highest: '#e4e2e4'
  on-surface: '#1b1b1d'
  on-surface-variant: '#44474c'
  inverse-surface: '#303032'
  inverse-on-surface: '#f2f0f2'
  outline: '#75777d'
  outline-variant: '#c5c6cd'
  surface-tint: '#525f75'
  primary: '#000000'
  on-primary: '#ffffff'
  primary-container: '#0e1c2f'
  on-primary-container: '#77849c'
  inverse-primary: '#bac7e1'
  secondary: '#426086'
  on-secondary: '#ffffff'
  secondary-container: '#b3d1fd'
  on-secondary-container: '#3b5a7f'
  tertiary: '#000000'
  on-tertiary: '#ffffff'
  tertiary-container: '#2a1702'
  on-tertiary-container: '#9d7e5e'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#d6e3fe'
  primary-fixed-dim: '#bac7e1'
  on-primary-fixed: '#0e1c2f'
  on-primary-fixed-variant: '#3a475c'
  secondary-fixed: '#d3e4ff'
  secondary-fixed-dim: '#aac9f4'
  on-secondary-fixed: '#001c38'
  on-secondary-fixed-variant: '#29486d'
  tertiary-fixed: '#ffddba'
  tertiary-fixed-dim: '#e4c09c'
  on-tertiary-fixed: '#2a1702'
  on-tertiary-fixed-variant: '#5a4226'
  background: '#fbf9fa'
  on-background: '#1b1b1d'
  surface-variant: '#e4e2e4'
typography:
  display-hero:
    fontFamily: Inter
    fontSize: 40px
    fontWeight: '700'
    lineHeight: 48px
    letterSpacing: -0.02em
  display-hero-mobile:
    fontFamily: Inter
    fontSize: 30px
    fontWeight: '700'
    lineHeight: 38px
    letterSpacing: -0.015em
  headline-lg:
    fontFamily: Inter
    fontSize: 28px
    fontWeight: '600'
    lineHeight: 36px
    letterSpacing: -0.015em
  headline-lg-mobile:
    fontFamily: Inter
    fontSize: 22px
    fontWeight: '600'
    lineHeight: 30px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Inter
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 24px
    letterSpacing: -0.005em
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: 0em
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: 0em
  body-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
    letterSpacing: 0.005em
  code-lg:
    fontFamily: JetBrains Mono
    fontSize: 18px
    fontWeight: '500'
    lineHeight: 26px
    letterSpacing: -0.02em
  code-md:
    fontFamily: JetBrains Mono
    fontSize: 13px
    fontWeight: '500'
    lineHeight: 18px
    letterSpacing: -0.01em
  code-sm:
    fontFamily: JetBrains Mono
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.01em
  label-caps:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.05em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1.5rem
  gutter-mobile: 0.75rem
  margin: 2rem
  margin-mobile: 1rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2.5rem
---

## Brand & Style

This design system embodies high-frequency institutional trust, cryptographic precision, and sovereign financial clarity. Tailored for mission-critical core banking, transaction clearing, and treasury management platforms, the interface balances executive authority with deep operational utility. It discards consumer-fintech whimsy in favor of dense, error-resistant workflows that instill absolute confidence in operations engineers, bank managers, and treasury controllers.

The visual style blends **Corporate Modern** with **Technical Precision Engineering**:
- **Purity and Structure**: Monolithic structures paired with razor-sharp micro-borders, deterministic spatial rhythm, and structured data layouts.
- **Architectural Hierarchy**: Deep navy foundations reinforce institutional permanence, paired with clinical emerald and ruby accents designed strictly for functional transaction semantics (credits vs. debits).
- **Audit-Grade Clarity**: Monospace treatments for transactional telemetry, ledger identifiers, and currency sequences eliminate visual ambiguity and streamline verification workflows.

## Colors

The palette establishes an immutable institutional hierarchy using deep naval foundations, precise semantic transaction indicators, and cool slate neutral surfaces.

### Surface and Structure
- **Canvas Root (`bg-canvas`)**: `#F8FAFC` (Slate 50) delivers clean, glare-free optical endurance during prolonged operational shifts.
- **Surface Elevation 1 (`bg-surface`)**: `#FFFFFF` provides clean card backgrounds and primary ledger containers.
- **Surface Elevation 2 (`bg-subtle`)**: `#F1F5F9` (Slate 100) separates metadata rows, table headers, and disabled panel sections.
- **Borders & Dividers**:
  - Crisp Structural (`border-subtle`): `#E2E8F0` (Slate 200) for standard layout and field perimeters.
  - Interactive Focus (`border-strong`): `#CBD5E1` (Slate 300) advancing to `#1E3E62` on interactive states.

### Core Brand Accents
- **Deep Navy Primary (`#0B192C`)**: Used for primary action buttons, key executive metrics, navigation sidebars, and high-emphasis interface chrome.
- **Steel Navy Secondary (`#1E3E62`)**: Secondary controls, active navigation items, table header label groups, and structural containers.

### Transactional & Status Semantics
- **Credit / Inflow Positive (`#00C853` / `#10B981`)**: Signals cleared deposits, inflow credits, and positive net delta. Paired with background `#ECFDF5` and stroke `#A7F3D0` for badges.
- **Debit / Outflow Negative (`#EF4444`)**: Indicates debits, fees, chargebacks, and failed ledger entries. Paired with background `#FEF2F2` and stroke `#FECACA`.
- **System Warning & Pending (`#F59E0B`)**: Used exclusively for in-flight authorizations, 202 accepted actions, and processing queues. Paired with background `#FFFBEB` and stroke `#FDE68A`.
- **Restricted / Blocked (`#64748B`)**: Identifies suspended ledgers, locked instruments, and revoked access states.

## Typography

The typographic hierarchy implements a high-utility hybrid engine: **Inter** handles narrative clarity, forms, and administrative layouts, while **JetBrains Mono** governs ledger telemetry, currency columns, financial amounts, and system IDs.

### Operational Rules
1. **Monospaced Data Anchoring**: All monetary amounts, balances (`balanceAfter`), account numbers, routing numbers, CPF/tax IDs, UUIDs, and idempotency keys must strictly use the `code-*` scale with tabular lining figures (`font-variant-numeric: tabular-nums lining-nums`). This guarantees column alignment across dense, live-streaming ledger views.
2. **Uppercase Labeling**: Use `label-caps` with strict uppercase casing for table headers, ledger metadata titles, balance status tags, and form group labels.
3. **Number Clarity**: Currency prefixes (e.g., `USD`, `BRL`, `EUR`) sit at 75% opacity next to their respective amounts, styled in the same font family and weight to prevent vertical jitter.

## Layout & Spacing

The layout model uses a **Fluid Grid with Breakpoint Pinning**, tailored for real-time operations dashboards.

### Grid Anatomy
- **Desktop (1280px and above)**: 12-column layout with fixed 260px collapsible sidebar navigation, 24px (`1.5rem`) gutters, and 32px (`2rem`) outer canvas margins. Maximizes data density without causing visual crowding.
- **Tablet (768px - 1279px)**: 8-column layout, 16px (`1rem`) gutters, 24px (`1.5rem`) canvas margins. Navigation collapses to an icon rail or top app bar.
- **Mobile (below 768px)**: 4-column layout, 12px (`0.75rem`) gutters, 16px (`1rem`) canvas margins. Side-by-side transaction metrics convert into vertically stacked metric carousels.

### Operational Spacing Rhythm
- Data tables enforce a compact 40px cell height (36px for dense views) with 12px horizontal padding.
- Balance overview cards maintain an internal grid padding of `space-lg` (24px) for desktop and `space-md` (16px) for mobile.
- Modal dialogues for sensitive wire transfers and approvals enforce a strict, unscrollable max-width of 540px.

## Elevation & Depth

This design system avoids exaggerated drop shadows and blurry frosted glass, relying on **Structural Tonal Layers and Low-Contrast Outlines** to prioritize enterprise clarity and rapid scanning.

### Surface Tiers
- **Layer 0 (Base Canvas)**: `#F8FAFC` — Foundation background.
- **Layer 1 (Card & Content Blocks)**: `#FFFFFF` bounded by a 1px solid stroke of `#E2E8F0`. Flat elevation, zero shadow in resting state.
- **Layer 2 (Floating Overlays, Popovers, Dropdowns)**: `#FFFFFF` bounded by `#CBD5E1` with an ambient shadow: `0 4px 12px -2px rgba(11, 25, 44, 0.08), 0 2px 6px -1px rgba(11, 25, 44, 0.04)`.
- **Layer 3 (Modals & Command Palettes)**: `#FFFFFF` with boundary stroke `#94A3B8` and a deep institutional shadow: `0 20px 25px -5px rgba(11, 25, 44, 0.14), 0 8px 10px -6px rgba(11, 25, 44, 0.06)`. Backdrops feature a solid 40% `#0B192C` dim with zero blur for clean hardware performance.

### Interactive State Elevation
- Resting table rows: Flat with 1px border-bottom (`#F1F5F9`).
- Hovered table rows: `#F8FAFC` subtle fill shift; zero elevation lift to preserve stable data baselines.

## Shapes

The interface implements **Level 2 (Rounded)** roundedness. Slightly softened geometry provides a balanced balance between institutional stability and modern approachability.

- **Base Radius (`0.5rem` / `8px`)**: Buttons, input fields, dropdown trigger buttons, system status badges, code snippet blocks, and table corner intersections.
- **Container Radius (`rounded-lg` / `1rem` / `16px`)**: Ledger overview cards, balance modules, modal windows, sliding drawers, and system notifications.
- **Large Panels (`rounded-xl` / `1.5rem` / `24px`)**: Restricted to full-screen ledger containers and multi-step onboarding modals.
- **Interactive Toggles / Avatars (`full` / `9999px`)**: Reserved exclusively for circular user avatars and boolean toggle switches. Badges and chips maintain structured alignment with standard container radii.

## Components

### 1. Buttons & Idempotent Actions
- **Primary Action**: Solid `#0B192C` fill, `#FFFFFF` text, 8px border radius. Hover: `#1E3E62`. Focus: 2px offset ring in `#1E3E62`.
- **Destructive Action**: Solid `#EF4444` fill, `#FFFFFF` text. Hover: `#DC2626`.
- **Secondary / Outline**: `#FFFFFF` fill, 1px border `#CBD5E1`, text `#0B192C`. Hover: `#F8FAFC`.
- **Async 202 Polling State**: When an action triggers an asynchronous transaction, the button disables immediately, presents a dual-ring spinner, and displays an authoritative processing state (e.g., `AUTHORIZING...`). The button remains locked against repeat clicks via client-generated `X-Idempotency-Key` tracking.

### 2. Status Badges
Constructed with a structured rectangular profile (8px radius, `label-caps` font, 4px vertical / 8px horizontal padding):
- `ACTIVE` / `COMPLETED`: Text `#065F46`, Background `#ECFDF5`, Border `#A7F3D0`.
- `PENDING_ACTIVATION` / `PROCESSING`: Text `#92400E`, Background `#FFFBEB`, Border `#FDE68A`. Preceded by a subtle pulsing dot.
- `BLOCKED`: Text `#991B1B`, Background `#FEF2F2`, Border `#FECACA`.
- `FAILED`: Text `#7F1D1D`, Background `#FEE2E2`, Border `#FCA5A5`.

### 3. Ledger Statement Table
- **Header**: `#F1F5F9` background, 32px height, uppercase `label-caps` typography in `#64748B`.
- **Data Rows**: 44px height, single-line border-bottom `#E2E8F0`. Cells feature monospaced figures aligned right for debits, credits, and running balances (`balanceAfter`).
- **Amounts**: Credits display with a mandatory `+` prefix in `#00C853`. Debits display with a `-` prefix in `#EF4444`.
- **Pagination**: Dedicated cursor-pagination bar (`NEXT_PAGE_TOKEN`, `PREV_PAGE_TOKEN`) anchored directly beneath the table body, indicating live synchronization timestamps.

### 4. Balance & Metric Cards
- White `#FFFFFF` base, 1px solid `#E2E8F0` border, 16px corner radius.
- Cards feature an upper eyebrow in `label-caps` (`#64748B`), a primary figure in `code-lg` (`#0B192C`), and a split footer displaying real-time available versus ledger-cleared totals.

### 5. Input Fields & Masked Lookups
- Standard inputs: 38px height, 1px `#CBD5E1` border, `#FFFFFF` surface, `body-md` typography.
- Masked Counterparty Lookups: Includes an embedded search accessory icon, debounced lookup indicators, and masked output formats (e.g., `ACME CORP (***.482.109-**)`) using `JetBrains Mono` for rapid identification.

### 6. Checkboxes & Radio Controls
- Square 16px with 4px corners (checkboxes) and 16px circular (radios).
- Active fill: `#0B192C`. Check indicators: crisp 1.5px white vector ticks, avoiding soft motion transitions.