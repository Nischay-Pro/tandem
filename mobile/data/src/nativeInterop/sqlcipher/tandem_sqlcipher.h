#ifndef TANDEM_SQLCIPHER_H
#define TANDEM_SQLCIPHER_H

#include <stdint.h>

typedef struct tandem_db tandem_db;
typedef struct tandem_stmt tandem_stmt;

int tandem_open(const char *path, tandem_db **db);
int tandem_key(tandem_db *db, const void *key, int length);
int tandem_close(tandem_db *db);
int tandem_errcode(tandem_db *db);
const char *tandem_errmsg(tandem_db *db);
int tandem_autocommit(tandem_db *db);

int tandem_prepare(tandem_db *db, const char *sql, tandem_stmt **stmt);
int tandem_finalize(tandem_stmt *stmt);
int tandem_step(tandem_stmt *stmt);
int tandem_reset(tandem_stmt *stmt);
int tandem_clear_bindings(tandem_stmt *stmt);
tandem_db *tandem_stmt_db(tandem_stmt *stmt);

int tandem_bind_blob(tandem_stmt *stmt, int index, const void *value, int length);
int tandem_bind_double(tandem_stmt *stmt, int index, double value);
int tandem_bind_int64(tandem_stmt *stmt, int index, int64_t value);
int tandem_bind_text(tandem_stmt *stmt, int index, const char *value, int length);
int tandem_bind_null(tandem_stmt *stmt, int index);

int tandem_column_count(tandem_stmt *stmt);
const char *tandem_column_name(tandem_stmt *stmt, int index);
int tandem_column_type(tandem_stmt *stmt, int index);
const void *tandem_column_blob(tandem_stmt *stmt, int index);
int tandem_column_bytes(tandem_stmt *stmt, int index);
double tandem_column_double(tandem_stmt *stmt, int index);
int64_t tandem_column_int64(tandem_stmt *stmt, int index);
const unsigned char *tandem_column_text(tandem_stmt *stmt, int index);

#endif
