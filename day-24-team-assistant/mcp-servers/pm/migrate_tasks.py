#!/usr/bin/env python3
"""
Migrate tasks.json to new format with metadata
"""

import json
import sys
from pathlib import Path

# Add parent directory to path
sys.path.append(str(Path(__file__).parent.parent))

from pm import config


def migrate_tasks():
    """Migrate tasks.json to new format"""
    if not config.TASKS_FILE.exists():
        print("No tasks.json found, nothing to migrate")
        return

    print("Migrating tasks.json...")

    # Load existing data
    with open(config.TASKS_FILE, 'r', encoding='utf-8') as f:
        data = json.load(f)

    # Check if already in new format
    if isinstance(data, dict) and "metadata" in data:
        print("Already in new format!")
        return

    # Extract last task ID from existing tasks
    last_id = 0
    if isinstance(data, list):
        for task in data:
            task_id = task.get("id", "")
            if task_id.startswith("task_"):
                try:
                    num = int(task_id.split("_")[1])
                    last_id = max(last_id, num)
                except (ValueError, IndexError):
                    pass

    # Create new format
    new_data = {
        "metadata": {
            "last_task_id": last_id
        },
        "tasks": data if isinstance(data, list) else []
    }

    # Backup old file
    backup_file = config.TASKS_FILE.with_suffix('.json.backup')
    with open(backup_file, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
    print(f"Created backup: {backup_file}")

    # Save new format
    with open(config.TASKS_FILE, 'w', encoding='utf-8') as f:
        json.dump(new_data, f, indent=2, ensure_ascii=False)

    print("Migration complete!")
    print(f"  Tasks: {len(new_data['tasks'])}")
    print(f"  Last task ID: {last_id}")


if __name__ == "__main__":
    migrate_tasks()
