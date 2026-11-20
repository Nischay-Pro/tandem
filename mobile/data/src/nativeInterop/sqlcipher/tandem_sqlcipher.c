/*
 * SQLCipher is compiled into this one translation unit with every SQLite symbol kept
 * private, so it can sit beside the plain SQLite that Room bundles without clashing.
 */
#define SQLITE_API static
#define SQLITE_HAS_CODEC 1
#define SQLITE_TEMP_STORE 2
#define SQLITE_THREADSAFE 2
#define SQLITE_EXTRA_INIT sqlcipher_extra_init
#define SQLITE_EXTRA_SHUTDOWN sqlcipher_extra_shutdown
#define SQLITE_OMIT_LOAD_EXTENSION 1
#define SQLITE_DEFAULT_FOREIGN_KEYS 0
#include "sqlite3.c"
#include "tandem_sqlcipher.h"

#define DB(db) ((sqlite3 *)(db))
#define STMT(stmt) ((sqlite3_stmt *)(stmt))

int tandem_open(const char *path, tandem_db **db) {
  return sqlite3_open_v2(path, (sqlite3 **)db, SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_NOMUTEX, NULL);
}
int tandem_key(tandem_db *db, const void *key, int length) { return sqlite3_key(DB(db), key, length); }
int tandem_close(tandem_db *db) { return sqlite3_close_v2(DB(db)); }
int tandem_errcode(tandem_db *db) { return sqlite3_extended_errcode(DB(db)); }
const char *tandem_errmsg(tandem_db *db) { return sqlite3_errmsg(DB(db)); }
int tandem_autocommit(tandem_db *db) { return sqlite3_get_autocommit(DB(db)); }

int tandem_prepare(tandem_db *db, const char *sql, tandem_stmt **stmt) {
  return sqlite3_prepare_v2(DB(db), sql, -1, (sqlite3_stmt **)stmt, NULL);
}
int tandem_finalize(tandem_stmt *stmt) { return sqlite3_finalize(STMT(stmt)); }
int tandem_step(tandem_stmt *stmt) { return sqlite3_step(STMT(stmt)); }
int tandem_reset(tandem_stmt *stmt) { return sqlite3_reset(STMT(stmt)); }
int tandem_clear_bindings(tandem_stmt *stmt) { return sqlite3_clear_bindings(STMT(stmt)); }
tandem_db *tandem_stmt_db(tandem_stmt *stmt) { return (tandem_db *)sqlite3_db_handle(STMT(stmt)); }

int tandem_bind_blob(tandem_stmt *stmt, int index, const void *value, int length) {
  return sqlite3_bind_blob(STMT(stmt), index, value, length, SQLITE_TRANSIENT);
}
int tandem_bind_double(tandem_stmt *stmt, int index, double value) { return sqlite3_bind_double(STMT(stmt), index, value); }
int tandem_bind_int64(tandem_stmt *stmt, int index, int64_t value) { return sqlite3_bind_int64(STMT(stmt), index, value); }
int tandem_bind_text(tandem_stmt *stmt, int index, const char *value, int length) {
  return sqlite3_bind_text(STMT(stmt), index, value, length, SQLITE_TRANSIENT);
}
int tandem_bind_null(tandem_stmt *stmt, int index) { return sqlite3_bind_null(STMT(stmt), index); }

int tandem_column_count(tandem_stmt *stmt) { return sqlite3_column_count(STMT(stmt)); }
const char *tandem_column_name(tandem_stmt *stmt, int index) { return sqlite3_column_name(STMT(stmt), index); }
int tandem_column_type(tandem_stmt *stmt, int index) { return sqlite3_column_type(STMT(stmt), index); }
const void *tandem_column_blob(tandem_stmt *stmt, int index) { return sqlite3_column_blob(STMT(stmt), index); }
int tandem_column_bytes(tandem_stmt *stmt, int index) { return sqlite3_column_bytes(STMT(stmt), index); }
double tandem_column_double(tandem_stmt *stmt, int index) { return sqlite3_column_double(STMT(stmt), index); }
int64_t tandem_column_int64(tandem_stmt *stmt, int index) { return sqlite3_column_int64(STMT(stmt), index); }
const unsigned char *tandem_column_text(tandem_stmt *stmt, int index) { return sqlite3_column_text(STMT(stmt), index); }
