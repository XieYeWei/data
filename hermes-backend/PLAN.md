# Hermes Platform - Product Enhancement Plan

## MVP Priority Order

### P0 - Critical Gaps (must fix now)
1. YARN Applications page - currently blank, implement full table + filters + kill
2. DataNode page - replace white box with proper table
3. Dashboard enhancements - health score, trend charts, quick actions

### P1 - Design System
4. Apply unified color palette (#0f172a / #1e2937 / #334155)
5. Component polish (buttons, badges, loading states)
6. Add skeleton loading

### P2 - Module Enhancements
7. JournalNode checkpoint - add manual trigger button
8. Log viewer - WebSocket auto-tail (polling fallback)
9. File system - batch operations, search, breadcrumbs

## Architecture Decisions
- All APIs under /api/v1/
- Frontend Vue 3 + Element Plus + dark theme
- Backend Spring Boot 3.2 + Hadoop FileSystem API
- YARN via YarnClient API
- Colors: bg #0f172a, card #1e2938, border #334155, primary #3b82f6
