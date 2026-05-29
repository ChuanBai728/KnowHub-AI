# 数据库初始化

## MySQL
```sql
SOURCE sql/Mysql/create_database_mysql.sql;
SOURCE sql/Mysql/create_table_mysql.sql;
```

## PostgreSQL/pgvector
```sql
\i sql/PostgresSql/create_database_postgres_sql.sql
\c knowhub_pgvector
\i sql/PostgresSql/create_table_postgres_sql.sql
```

## Elasticsearch 索引名称
- `knowhub-document-keyword`
- `knowhub-document-navigation`
- `knowhub-knowledge-route`

索引由应用启动时自动创建，无需手动执行。
