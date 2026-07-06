#!/usr/bin/env python3
"""Import TranslationApp incremental SQL exports into MySQL."""

import argparse
import json
import subprocess
import sys
from pathlib import Path

import pymysql

MYSQL_BIN = r"D:\software\mysql8.3\bin\mysql.exe"
IMPORT_ORDER = [
    "permissions.sql", "menus.sql", "roles.sql", "users.sql",
    "role_permissions.sql", "role_menus.sql", "user_roles.sql", "user_oauth_bindings.sql",
    "departments.sql", "user_departments.sql", "crawl_tasks.sql", "documents.sql",
    "friendships.sql", "friend_requests.sql", "conversations.sql", "conversation_members.sql",
    "attachments.sql", "messages.sql", "message_reads.sql", "call_sessions.sql", "call_participants.sql",
]


def run_mysql_cli(args, sql_text: str, database=None):
    cmd = [MYSQL_BIN, "-h", args.host, "-P", str(args.port), "-u", args.user, f"-p{args.password}",
           "--default-character-set=utf8mb4"]
    if database:
        cmd.append(database)
    proc = subprocess.run(cmd, input=sql_text.encode("utf-8"), capture_output=True)
    stderr = proc.stderr.decode("utf-8", errors="replace")
    if proc.returncode != 0:
        raise RuntimeError(stderr.strip() or f"mysql exited with {proc.returncode}")


def connect(args):
    return pymysql.connect(
        host=args.host,
        port=args.port,
        user=args.user,
        password=args.password,
        database=args.database,
        charset="utf8mb4",
        autocommit=False,
    )


def init_schema(args, root: Path):
    run_mysql_cli(args, f"DROP DATABASE IF EXISTS {args.database}; "
                    f"CREATE DATABASE {args.database} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;")
    schema = (root / "backend" / "src" / "main" / "resources" / "db" / "schema.sql").read_text(encoding="utf-8-sig")
    migration_im = (root / "backend" / "src" / "main" / "resources" / "db" / "migration-im.sql").read_text(encoding="utf-8-sig")
    run_mysql_cli(args, schema, args.database)
    run_mysql_cli(args, migration_im, args.database)
    run_mysql_cli(args, (
        "ALTER TABLE crawl_tasks MODIFY COLUMN table_of_contents MEDIUMTEXT NULL;"
    ), args.database)


def normalize_sql_text(text: str) -> str:
    if text.startswith("\ufeff"):
        text = text.lstrip("\ufeff")
    text = text.replace("_binary '\\0'", "0")
    text = text.replace("_binary '\x01'", "1")
    text = text.replace("insert into translation_app.", "INSERT INTO ")
    return text


def extract_statements(text: str):
    """Extract executable INSERT/LOCK/UNLOCK statements from dump or plain SQL."""
    statements = []
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line or line.startswith("--"):
            continue
        if line.startswith("/*") or line.startswith("/*!"):
            continue
        upper = line.upper()
        if upper.startswith("INSERT INTO") or upper.startswith("LOCK TABLES") or upper.startswith("UNLOCK TABLES"):
            statements.append(line.rstrip(";"))
    return statements


def import_file(conn, path: Path):
    text = normalize_sql_text(path.read_text(encoding="utf-8-sig"))
    statements = extract_statements(text)
    with conn.cursor() as cur:
        cur.execute("SET FOREIGN_KEY_CHECKS=0")
        cur.execute("SET NAMES utf8mb4")
        for stmt in statements:
            if stmt.upper().startswith("LOCK TABLES") or stmt.upper().startswith("UNLOCK TABLES"):
                continue
            cur.execute(stmt)
        cur.execute("SET FOREIGN_KEY_CHECKS=1")
    conn.commit()


def main():
    parser = argparse.ArgumentParser(description="Import incremental SQL export")
    parser.add_argument("--dir", required=True, help="Export folder")
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", type=int, default=3306)
    parser.add_argument("--user", default="root")
    parser.add_argument("--password", default="1234qwer")
    parser.add_argument("--database", default="translation_app")
    parser.add_argument("--skip-init", action="store_true")
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[1]
    import_dir = Path(args.dir)
    if not import_dir.is_absolute():
        import_dir = root / import_dir
    if not import_dir.exists():
        print(f"Import directory not found: {import_dir}", file=sys.stderr)
        sys.exit(1)

    if not args.skip_init:
        print("Initializing database schema...")
        init_schema(args, root)

    meta_path = import_dir / "metadata.json"
    if meta_path.exists():
        meta = json.loads(meta_path.read_text(encoding="utf-8-sig"))
        print(f"Importing export from {meta.get('exportTime', import_dir.name)} ({meta.get('tableCount', '?')} tables)")

    fallback_dir = root / "data-exports" / "incremental" / "2"
    # mysqldump exports with JSON break the Windows mysql client; use clean inserts when available
    fallback_files = {"users.sql", "crawl_tasks.sql", "messages.sql"}
    conn = connect(args)

    try:
        for filename in IMPORT_ORDER:
            path = import_dir / filename
            if filename in fallback_files and (fallback_dir / filename).exists():
                path = fallback_dir / filename
                print(f"  {filename} (clean-format fallback from incremental/2)")
            elif not path.exists():
                print(f"  SKIP {filename}")
                continue
            else:
                print(f"  {filename}")
            import_file(conn, path)
    finally:
        conn.close()

    conn = connect(args)
    with conn.cursor() as cur:
        cur.execute(
            "SELECT 'users' t, COUNT(*) c FROM users UNION ALL "
            "SELECT 'crawl_tasks', COUNT(*) FROM crawl_tasks UNION ALL "
            "SELECT 'documents', COUNT(*) FROM documents UNION ALL "
            "SELECT 'messages', COUNT(*) FROM messages UNION ALL "
            "SELECT 'conversations', COUNT(*) FROM conversations"
        )
        rows = cur.fetchall()
    conn.close()

    print("\nImport complete. Row counts:")
    for row in rows:
        print(f"  {row[0]}: {row[1]}")
    print("\nExpected (metadata): users=11, crawl_tasks=37, documents=5958, messages=26")


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"ERROR: {e}", file=sys.stderr)
        sys.exit(1)
