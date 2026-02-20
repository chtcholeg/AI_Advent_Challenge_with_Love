"""Dashboard service for project statistics and metrics."""

import logging
from collections import defaultdict
from datetime import datetime, timedelta, timezone
from typing import Any

from . import config

logger = logging.getLogger(__name__)


class DashboardService:
    """Service for project dashboard and team workload"""

    def get_dashboard(self, project_id: str) -> dict[str, Any]:
        """Get project dashboard with statistics"""
        from .main import load_tasks

        tasks = load_tasks()
        project_tasks = [t for t in tasks if t.get("project_id") == project_id]

        if not project_tasks:
            return {
                "success": False,
                "error": "No tasks found for this project"
            }

        # Status distribution
        status_counts = defaultdict(int)
        for task in project_tasks:
            status_counts[task["status"]] += 1

        # Priority distribution
        priority_counts = defaultdict(int)
        for task in project_tasks:
            priority_counts[task["priority"]] += 1

        # Calculate metrics
        total_tasks = len(project_tasks)
        completed_tasks = status_counts.get("done", 0)
        in_progress_tasks = status_counts.get("in_progress", 0)
        blocked_tasks = status_counts.get("blocked", 0)

        completion_rate = (completed_tasks / total_tasks * 100) if total_tasks > 0 else 0

        # Time tracking
        total_estimated = sum(t.get("estimated_hours", 0) for t in project_tasks)
        total_spent = sum(t.get("spent_hours", 0) for t in project_tasks)

        # Overdue tasks
        now = datetime.now(timezone.utc)
        overdue_tasks = []
        for task in project_tasks:
            if task["status"] not in ["done", "cancelled"]:
                due_date_str = task.get("due_date")
                if due_date_str:
                    try:
                        # Parse date - handle both date-only and datetime formats
                        if "T" in due_date_str or "Z" in due_date_str:
                            # Full datetime with timezone
                            due_date = datetime.fromisoformat(due_date_str.replace("Z", "+00:00"))
                        else:
                            # Date only - treat as end of day UTC
                            due_date = datetime.fromisoformat(due_date_str).replace(
                                hour=23, minute=59, second=59, tzinfo=timezone.utc
                            )

                        if due_date < now:
                            overdue_tasks.append({
                                "id": task["id"],
                                "title": task["title"],
                                "due_date": due_date_str,
                                "days_overdue": (now - due_date).days
                            })
                    except ValueError:
                        pass

        # Recent activity (tasks updated in last 7 days)
        week_ago = now - timedelta(days=7)
        recent_activity = []
        for task in sorted(project_tasks, key=lambda t: t.get("updated_at", ""), reverse=True)[:10]:
            try:
                updated_at = datetime.fromisoformat(task["updated_at"].replace("Z", "+00:00"))
                if updated_at > week_ago:
                    recent_activity.append({
                        "id": task["id"],
                        "title": task["title"],
                        "status": task["status"],
                        "updated_at": task["updated_at"]
                    })
            except (ValueError, KeyError):
                pass

        # Critical tasks
        critical_tasks = [
            {
                "id": t["id"],
                "title": t["title"],
                "status": t["status"],
                "assignee": t.get("assignee", "unassigned")
            }
            for t in project_tasks
            if t["priority"] == "critical" and t["status"] not in ["done", "cancelled"]
        ]

        # Velocity calculation (tasks completed per week)
        completed_per_week = self._calculate_velocity(project_tasks)

        dashboard = {
            "success": True,
            "project_id": project_id,
            "timestamp": now.isoformat(),
            "summary": {
                "total_tasks": total_tasks,
                "completed_tasks": completed_tasks,
                "in_progress_tasks": in_progress_tasks,
                "blocked_tasks": blocked_tasks,
                "completion_rate": round(completion_rate, 2)
            },
            "status_distribution": dict(status_counts),
            "priority_distribution": dict(priority_counts),
            "time_tracking": {
                "total_estimated_hours": total_estimated,
                "total_spent_hours": total_spent,
                "efficiency": round((total_estimated / total_spent * 100) if total_spent > 0 else 100, 2)
            },
            "overdue_tasks": overdue_tasks,
            "critical_tasks": critical_tasks,
            "recent_activity": recent_activity,
            "velocity": {
                "tasks_per_week": completed_per_week,
                "estimated_completion": self._estimate_completion(
                    total_tasks - completed_tasks,
                    completed_per_week
                )
            }
        }

        logger.info(f"Dashboard generated for project {project_id}")

        return dashboard

    def get_team_workload(self, project_id: str) -> dict[str, Any]:
        """Get team workload by assignee"""
        from .main import load_tasks

        tasks = load_tasks()
        project_tasks = [t for t in tasks if t.get("project_id") == project_id]

        if not project_tasks:
            return {
                "success": False,
                "error": "No tasks found for this project"
            }

        # Group by assignee
        workload = defaultdict(lambda: {
            "total_tasks": 0,
            "open_tasks": 0,
            "in_progress_tasks": 0,
            "completed_tasks": 0,
            "estimated_hours": 0,
            "spent_hours": 0,
            "tasks": []
        })

        for task in project_tasks:
            assignee = task.get("assignee", "unassigned")
            workload[assignee]["total_tasks"] += 1

            if task["status"] == "open":
                workload[assignee]["open_tasks"] += 1
            elif task["status"] == "in_progress":
                workload[assignee]["in_progress_tasks"] += 1
            elif task["status"] == "done":
                workload[assignee]["completed_tasks"] += 1

            workload[assignee]["estimated_hours"] += task.get("estimated_hours", 0)
            workload[assignee]["spent_hours"] += task.get("spent_hours", 0)

            workload[assignee]["tasks"].append({
                "id": task["id"],
                "title": task["title"],
                "status": task["status"],
                "priority": task["priority"]
            })

        # Calculate load score for each assignee
        for assignee, data in workload.items():
            active_tasks = data["open_tasks"] + data["in_progress_tasks"]
            # Load score: weighted by priority and task count
            load_score = active_tasks * 0.5 + (data["estimated_hours"] - data["spent_hours"]) * 0.1
            data["load_score"] = round(load_score, 2)
            data["utilization"] = round(
                (data["spent_hours"] / data["estimated_hours"] * 100)
                if data["estimated_hours"] > 0 else 0,
                2
            )

        logger.info(f"Team workload calculated for {len(workload)} assignees")

        return {
            "success": True,
            "project_id": project_id,
            "assignees": dict(workload),
            "summary": {
                "total_assignees": len(workload),
                "most_loaded": max(workload.items(), key=lambda x: x[1]["load_score"])[0]
                if workload else "none",
                "least_loaded": min(workload.items(), key=lambda x: x[1]["load_score"])[0]
                if workload else "none"
            }
        }

    def _calculate_velocity(self, tasks: list[dict[str, Any]]) -> float:
        """Calculate velocity (tasks completed per week)"""
        completed_tasks = [t for t in tasks if t["status"] == "done"]

        if not completed_tasks:
            return 0.0

        # Find date range
        dates = []
        for task in completed_tasks:
            try:
                updated_at = datetime.fromisoformat(task["updated_at"].replace("Z", "+00:00"))
                dates.append(updated_at)
            except (ValueError, KeyError):
                pass

        if not dates or len(dates) < 2:
            return 0.0

        earliest = min(dates)
        latest = max(dates)
        weeks = max((latest - earliest).days / 7, 1)

        velocity = len(completed_tasks) / weeks

        return round(velocity, 2)

    def _estimate_completion(self, remaining_tasks: int, velocity: float) -> str:
        """Estimate project completion time"""
        if velocity <= 0:
            return "Unknown (no velocity data)"

        weeks_remaining = remaining_tasks / velocity
        days_remaining = int(weeks_remaining * 7)

        if days_remaining < 7:
            return f"{days_remaining} days"
        elif days_remaining < 30:
            return f"{int(weeks_remaining)} weeks"
        else:
            months = int(days_remaining / 30)
            return f"{months} months"
