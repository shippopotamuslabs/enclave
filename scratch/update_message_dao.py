import re
import os

path = "/home/sean/Code/pocket-crew/core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/MessageDao.kt"

with open(path, 'r') as f:
    content = f.read()

# Add deleted = 0 to SELECTs
content = re.sub(r'SELECT \* FROM message WHERE (?!deleted = 0)', r'SELECT * FROM message WHERE deleted = 0 AND ', content)
# Special case for no WHERE clause
content = re.sub(r'SELECT \* FROM message ORDER', r'SELECT * FROM message WHERE deleted = 0 ORDER', content)

# Add updated_at = :timestamp to UPDATEs
# Find all UPDATE message SET ... WHERE id = :id
def update_sub(match):
    query = match.group(1)
    if "updated_at" in query:
        return match.group(0)
    
    # Insert updated_at = :timestamp before WHERE
    new_query = query.replace(" WHERE", ", updated_at = :timestamp WHERE")
    
    # Find the function signature following this query
    # and add timestamp: Long = System.currentTimeMillis()
    # This is hard because the query is in an annotation.
    return match.group(0).replace(" WHERE", ", updated_at = :timestamp WHERE")

content = re.sub(r'(@Query\("UPDATE message SET .*? WHERE id = :.*?"\))', update_sub, content)

# Now add the parameter to the functions
# This is tricky. Let's do it manually for the main ones or use a more specific regex.

with open(path, 'w') as f:
    f.write(content)
