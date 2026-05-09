import os
import re

entities = [
    "DefaultModelEntity.kt",
    "StudioMediaEntity.kt",
    "WebSearchSourceEntity.kt",
    "StudioAlbumEntity.kt",
    "MessageFileEntity.kt",
    "LocalModelEntity.kt",
    "LocalModelConfigurationEntity.kt",
    "ApiModelConfigurationEntity.kt",
    "MemoriesEntity.kt",
    "TtsProviderEntity.kt",
    "MessageVisionAnalysisEntity.kt",
    "MediaProviderEntity.kt",
    "ApiCredentialsEntity.kt",
    "ChatEntity.kt",
    "MessageEntity.kt"
]

path = "/home/sean/Code/pocket-crew/core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/"

for entity in entities:
    file_path = os.path.join(path, entity)
    with open(file_path, 'r') as f:
        content = f.read()
    
    modified = False
    
    # Check for updatedAt
    if "updatedAt" not in content and "updated_at" not in content:
        # Insert before pendingSync or before closing paren
        if "pendingSync" in content:
            content = re.sub(r'(\s+)(val pendingSync: Int.*?,?)', r'\1@ColumnInfo(name = "updated_at")\1val updatedAt: Long = System.currentTimeMillis(),\1\2', content)
            modified = True
    
    # Check for deleted
    if "deleted" not in content:
        # Insert after pendingSync
        if "pendingSync" in content:
            # Find the line with pendingSync and insert after it
            content = re.sub(r'(val pendingSync: Int.*?)(,?\s*\))', r'\1,\1@ColumnInfo(name = "deleted")\1val deleted: Boolean = false\2', content)
            # That regex is a bit risky. Let's try simpler.
            match = re.search(r'val pendingSync: Int.*?\n', content)
            if match:
                end = match.end()
                indent = re.search(r'^\s*', match.group()).group()
                insertion = f'{indent}@ColumnInfo(name = "deleted")\n{indent}val deleted: Boolean = false,\n'
                # Check if pendingSync line needs a comma
                line = match.group()
                if ',' not in line and ')' not in line:
                    # This is complex. Let's just use string replacement on the whole block.
                    pass

    # NEW ATTEMPT with simpler logic
    with open(file_path, 'r') as f:
        lines = f.readlines()
    
    new_lines = []
    has_updated_at = any("updatedAt" in l or "updated_at" in l for l in lines)
    has_deleted = any("deleted" in l for l in lines)
    
    if not has_updated_at or not has_deleted:
        for i, line in enumerate(lines):
            if "pendingSync" in line or "pending_sync" in line:
                if not has_updated_at:
                    indent = line[:line.find("@")] if "@" in line else "    "
                    new_lines.append(f'{indent}@ColumnInfo(name = "updated_at")\n')
                    new_lines.append(f'{indent}val updatedAt: Long = System.currentTimeMillis(),\n')
                
                # If the pendingSync line is the last one (ends with ) or is missing a comma)
                if ")" in line and "(" not in line: # Likely the end of data class
                    line = line.replace(")", ",")
                    new_lines.append(line)
                    if not has_deleted:
                        indent = line[:line.find("@")] if "@" in line else "    "
                        new_lines.append(f'{indent}@ColumnInfo(name = "deleted")\n')
                        new_lines.append(f'{indent}val deleted: Boolean = false\n')
                        new_lines.append(")\n")
                    else:
                         new_lines.append(")\n")
                else:
                    if not line.strip().endswith(",") and not line.strip().endswith(")"):
                        line = line.rstrip() + ",\n"
                    new_lines.append(line)
                    if not has_deleted and ")" not in line:
                        # We'll add deleted after pendingSync if it's not the last line
                        pass 
            elif ")" in line and i > 0 and ("pendingSync" in lines[i-1] or "pending_sync" in lines[i-1]) and not has_deleted:
                 # Add deleted before the closing paren
                 indent = lines[i-1][:lines[i-1].find("@")] if "@" in lines[i-1] else "    "
                 new_lines.append(f'{indent}@ColumnInfo(name = "deleted")\n')
                 new_lines.append(f'{indent}val deleted: Boolean = false\n')
                 new_lines.append(line)
            else:
                new_lines.append(line)
        
        with open(file_path, 'w') as f:
            f.writelines(new_lines)
        print(f"Refined update for {entity}")
