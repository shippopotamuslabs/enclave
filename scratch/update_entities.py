import os

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
    "ApiCredentialsEntity.kt"
]

path = "/home/sean/Code/pocket-crew/core/data/src/main/kotlin/com/browntowndev/pocketcrew/core/data/local/"

for entity in entities:
    file_path = os.path.join(path, entity)
    with open(file_path, 'r') as f:
        content = f.read()
    
    if "updated_at" not in content:
        # Find the last closing parenthesis before the end of the class
        # Assuming data class format
        if "pending_sync" in content:
            # Add after pendingSync
            lines = content.splitlines()
            new_lines = []
            for line in lines:
                new_lines.append(line)
                if "pending_sync" in line:
                    # Clean up the line (remove trailing ) if it exists on the same line)
                    if line.strip().endswith(")"):
                        new_lines[-1] = line.replace(")", ",")
                        new_lines.append("    @ColumnInfo(name = \"updated_at\")")
                        new_lines.append("    val updatedAt: Long = System.currentTimeMillis(),")
                        new_lines.append("    @ColumnInfo(name = \"deleted\")")
                        new_lines.append("    val deleted: Boolean = false")
                        new_lines.append(")")
                    else:
                        new_lines.append("    @ColumnInfo(name = \"updated_at\")")
                        new_lines.append("    val updatedAt: Long = System.currentTimeMillis(),")
                        new_lines.append("    @ColumnInfo(name = \"deleted\")")
                        new_lines.append("    val deleted: Boolean = false")
            
            with open(file_path, 'w') as f:
                f.write("\n".join(new_lines) + "\n")
            print(f"Updated {entity}")
