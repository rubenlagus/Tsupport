/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.tsupport.SQLite;

import org.tsupport.messenger.FileLog;
import org.tsupport.ui.ApplicationLoader;

import java.util.HashMap;
import java.util.Map;

public class SQLiteDatabase {
	//private final int sqliteHandle;
    private final int sqliteHandleCache;
    private final int sqliteHandleInternal;

	private final Map<String, SQLitePreparedStatement> preparedMap = new HashMap<String, SQLitePreparedStatement>();
	private boolean isOpen = false;
    //private boolean inTransaction = false;
    private boolean isInTransactionCache = false;
    private boolean isInTransactionInternal = false;

/*	public int getSQLiteHandle() {
		return sqliteHandle;
	}*/

    public int getSQLiteHandleCache() {
        return sqliteHandleCache;
    }

    public int getSQLiteHandleInternal() {
        return sqliteHandleInternal;
    }

	public SQLiteDatabase(String fileNameCache, String fileNameInternal) throws SQLiteException {
		//sqliteHandle = opendb(fileName, ApplicationLoader.applicationContext.getFilesDir().getPath());
        sqliteHandleInternal = opendb(fileNameInternal, ApplicationLoader.applicationContext.getFilesDir().getPath());
        sqliteHandleCache = opendb(fileNameCache, ApplicationLoader.applicationContext.getFilesDir().getPath());
        isOpen = true;
	}

	public boolean tableExistsBoth(String tableName) throws SQLiteException {
		checkOpened();
		String s = "SELECT rowid FROM sqlite_master WHERE type='table' AND name=?;";
		return executeIntCache(s, tableName) != null || executeIntInternal(s, tableName) != null;
	}

    public boolean tableExistsCache(String tableName) throws SQLiteException {
        checkOpened();
        String s = "SELECT rowid FROM sqlite_master WHERE type='table' AND name=?;";
        return executeIntCache(s, tableName) != null;
    }

    public boolean tableExistsInternal(String tableName) throws SQLiteException {
        checkOpened();
        String s = "SELECT rowid FROM sqlite_master WHERE type='table' AND name=?;";
        return executeIntInternal(s, tableName) != null;
    }

/*	public void execute(String sql, Object... args) throws SQLiteException {
		checkOpened();
		SQLiteCursor cursor = query(sql, args);
		try {
			cursor.next();
		} finally {
			cursor.dispose();
		}
	}*/

    public void executeCache(String sql, Object... args) throws SQLiteException {
        checkOpened();
        SQLiteCursor cursor = queryCache(sql, args);
        try {
            cursor.next();
        } finally {
            cursor.dispose();
        }
    }

    public void executeInternal(String sql, Object... args) throws SQLiteException {
        checkOpened();
        SQLiteCursor cursor = queryInternal(sql, args);
        try {
            cursor.next();
        } finally {
            cursor.dispose();
        }
    }

/*    public SQLitePreparedStatement executeFast(String sql) throws SQLiteException {
        return new SQLitePreparedStatement(this, sql, true);
    }*/

    public SQLitePreparedStatement executeFastCache(String sql) throws SQLiteException {
        return new SQLitePreparedStatement(this, sql, true ,true);
    }

    public SQLitePreparedStatement executeFastInternal(String sql) throws SQLiteException {
        return new SQLitePreparedStatement(this, sql, false ,true);
    }

/*	public Integer executeInt(String sql, Object... args) throws SQLiteException {
		checkOpened();
		SQLiteCursor cursor = query(sql, args);
		try {
			if (!cursor.next()) {
				return null;
			}
			return cursor.intValue(0);
		} finally {
			cursor.dispose();
		}
	}*/

    public Integer executeIntCache(String sql, Object... args) throws SQLiteException {
        checkOpened();
        SQLiteCursor cursor = queryCache(sql, args);
        try {
            if (!cursor.next()) {
                return null;
            }
            return cursor.intValue(0);
        } finally {
            cursor.dispose();
        }
    }

    public Integer executeIntInternal(String sql, Object... args) throws SQLiteException {
        checkOpened();
        SQLiteCursor cursor = queryInternal(sql, args);
        try {
            if (!cursor.next()) {
                return null;
            }
            return cursor.intValue(0);
        } finally {
            cursor.dispose();
        }
    }

	/*public SQLiteCursor query(String sql, Object... args) throws SQLiteException {
		checkOpened();
		SQLitePreparedStatement stmt = preparedMap.get(sql);

		if (stmt == null) {
			stmt = new SQLitePreparedStatement(this, sql, false);
			preparedMap.put(sql, stmt);
		}

		return stmt.query(args);
	}*/

    public SQLiteCursor queryCache(String sql, Object... args) throws SQLiteException {
        checkOpened();
        SQLitePreparedStatement stmt = preparedMap.get(sql);

        if (stmt == null) {
            stmt = new SQLitePreparedStatement(this, sql, true, false);
            preparedMap.put(sql, stmt);
        }

        return stmt.query(args);
    }

    public SQLiteCursor queryInternal(String sql, Object... args) throws SQLiteException {
        checkOpened();
        SQLitePreparedStatement stmt = preparedMap.get(sql);

        if (stmt == null) {
            stmt = new SQLitePreparedStatement(this, sql, false, false);
            preparedMap.put(sql, stmt);
        }

        return stmt.query(args);
    }

/*
	public SQLiteCursor queryFinalized(String sql, Object... args) throws SQLiteException {
		checkOpened();
		return new SQLitePreparedStatement(this, sql, true).query(args);
	}
*/

    public SQLiteCursor queryFinalizedCache(String sql, Object... args) throws SQLiteException {
        checkOpened();
        return new SQLitePreparedStatement(this, sql, true, true).query(args);
    }

    public SQLiteCursor queryFinalizedInternal(String sql, Object... args) throws SQLiteException {
        checkOpened();
        return new SQLitePreparedStatement(this, sql, false, true).query(args);
    }


    public void close() {
		if (isOpen) {
			try {
				for (SQLitePreparedStatement stmt : preparedMap.values()) {
					stmt.finalizeQuery();
				}
                commitTransactionCache();
                commitTransactionInternal();
                closedb(sqliteHandleCache);
                closedb(sqliteHandleInternal);
				//closedb(sqliteHandle);
			} catch (SQLiteException e) {
                FileLog.e("tsupport", e.getMessage(), e);
			}
			isOpen = false;
		}
	}

	void checkOpened() throws SQLiteException {
		if (!isOpen) {
			throw new SQLiteException("Database closed");
		}
	}

	public void finalize() throws Throwable {
        super.finalize();
		close();
	}

    private StackTraceElement[] temp;
/*    public void beginTransaction() throws SQLiteException {
        if (inTransaction) {
            throw new SQLiteException("database already in transaction");
        }
        inTransaction = true;
        beginTransaction(sqliteHandle);
    }*/

    public void beginTransactionCache() throws SQLiteException {
        if (isInTransactionCache) {
            throw new SQLiteException("database already in transaction");
        }
        isInTransactionCache = true;
        beginTransaction(sqliteHandleCache);
    }

    public void beginTransactionInternal() throws SQLiteException {
        if (isInTransactionInternal) {
            throw new SQLiteException("database already in transaction");
        }
        isInTransactionInternal = true;
        beginTransaction(sqliteHandleInternal);
    }

/*    public void commitTransaction() {
        if (!inTransaction) {
            return;
        }
        inTransaction = false;
        commitTransaction(sqliteHandle);
    }*/

    public void commitTransactionCache() {
        if (!isInTransactionCache) {
            return;
        }
        isInTransactionCache = false;
        commitTransaction(sqliteHandleCache);
    }

    public void commitTransactionInternal() {
        if (!isInTransactionInternal) {
            return;
        }
        isInTransactionInternal = false;
        commitTransaction(sqliteHandleInternal);
    }

	native int opendb(String fileName, String tempDir) throws SQLiteException;
	native void closedb(int sqliteHandle) throws SQLiteException;
    native void beginTransaction(int sqliteHandle);
    native void commitTransaction(int sqliteHandle);
}
