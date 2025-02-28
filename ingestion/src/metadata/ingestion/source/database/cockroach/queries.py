#  Copyright 2021 Collate
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#  http://www.apache.org/licenses/LICENSE-2.0
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
"""
SQL Queries used during ingestion
"""

from sqlalchemy import text

COCKROACH_GET_TABLE_NAMES = """
    SELECT 
    c.relname AS table_name,
    c.relkind AS relkind
    FROM 
        pg_class c
    JOIN 
        pg_namespace n ON n.oid = c.relnamespace
    WHERE 
        n.nspname = :schema 
        AND c.relkind IN ('r', 'p', 'f')
    ORDER BY 
        c.relname
"""

COCKROACH_GET_VIEW_NAMES = """
    SELECT 
    c.relname AS table_name,
    c.relkind AS relkind
    FROM 
        pg_class c
    JOIN 
        pg_namespace n ON n.oid = c.relnamespace
    WHERE 
        n.nspname = :schema 
        AND c.relkind IN ('v')
    ORDER BY 
        c.relname
"""


COCKROACH_SCHEMA_COMMENTS = """
    SELECT
    current_database() AS database_name,
    n.nspname AS schema_name,
    d.description AS comment
FROM 
    pg_namespace n
LEFT JOIN 
    pg_description d 
ON 
    n.oid = d.objoid
WHERE 
    d.objsubid = 0;
"""


COCKROACH_GET_DATABASE = text(
    """
    select datname FROM pg_catalog.pg_database
"""
)

COCKROACH_GET_DB_NAMES = """
    select datname from pg_catalog.pg_database
"""
