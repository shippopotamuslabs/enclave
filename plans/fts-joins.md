# FTS Joins Fix for String Message IDs

## Objective
Fix full-text search queries for messages and chats. The queries currently join `message.id = message_search.rowid`, but since `MessageEntity.id` was changed to a String (`MessageId`), it is no longer the implicit SQLite `rowid`. This causes the joins to fail and return empty results.

## Key Files & Context
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/MessageDao.kt`
- `core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/ChatDao.kt`

## Background & Rationale
Room's `@Fts4(contentEntity = MessageEntity::class)` automatically creates triggers that sync the `rowid` of the content entity (`message`) to the `docid` (which aliases to `rowid`) of the FTS table (`message_search`). When `MessageEntity.id` was an integer primary key, it acted as the `rowid`. Now that it is a String, we must explicitly join on SQLite's internal `rowid` column.

Using `message.rowid = message_search.rowid` is highly efficient because `rowid` acts as the implicit primary key for both the main table (B-tree) and the FTS virtual table, meaning lookups are O(1) and will not result in a full table scan.

## Implementation Steps
1. **Update `MessageDao.searchMessages`**
   Change the JOIN condition from `message.id = message_search.rowid` to `message.rowid = message_search.rowid`.
   ```sql
   SELECT message.* FROM message
   JOIN message_search ON message.rowid = message_search.rowid
   WHERE message_search MATCH :query
   ```

2. **Update `ChatDao.searchChats`**
   Change the JOIN condition from `message.id = message_search.rowid` to `message.rowid = message_search.rowid`.
   ```sql
   SELECT * FROM (
       SELECT chat.* FROM chat
       JOIN message ON chat.id = message.chat_id
       JOIN message_search ON message.rowid = message_search.rowid
       WHERE message_search MATCH :ftsQuery
       
       UNION
       
       SELECT * FROM chat
       WHERE name LIKE '%' || :query || '%'
   )
   ORDER BY pinned DESC, last_modified DESC
   ```

## Verification & Testing
- Build the app to ensure no compilation or Room validation errors.
- Verify that searching for a known word in a message successfully returns the corresponding message/chat.