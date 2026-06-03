# Release Notes

## Database Changes (Schema Version 8)

### Destructive Database Upgrade (v5 to v8)
To support the psychological profile analysis feature and associated data persistence, the database schema version has been bumped from `5` to `8` on this branch.

- **Implementation Details**: The database is configured with `fallbackToDestructiveMigration(dropAllTables = true)`.
- **User Impact**: Upon upgrading, Room will automatically drop all existing local database tables and recreate them matching the new schema. This will clear all existing local chat history and local preferences, requiring a fresh login/setup.
