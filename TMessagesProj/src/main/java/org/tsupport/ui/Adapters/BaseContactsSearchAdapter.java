/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.tsupport.ui.Adapters;

import org.tsupport.android.AndroidUtilities;
import org.tsupport.android.MessagesController;
import org.tsupport.messenger.ConnectionsManager;
import org.tsupport.messenger.FileLog;
import org.tsupport.messenger.RPCRequest;
import org.tsupport.messenger.TLObject;
import org.tsupport.messenger.TLRPC;

import java.util.ArrayList;

public class BaseContactsSearchAdapter extends BaseFragmentAdapter {

    private long reqId = 0;
    private int lastReqId;
    protected String lastFoundUsername = null;

    public void queryServerSearch(final String query) {
        if (query == null || query.length() < 5) {
            if (reqId != 0) {
                ConnectionsManager.getInstance().cancelRpc(reqId, true);
                reqId = 0;
            }
            MessagesController.getInstance().globalSearched.clear();
            lastReqId = 0;
            notifyDataSetChanged();
            return;
        }
        TLRPC.TL_contacts_search req = new TLRPC.TL_contacts_search();
        req.q = query;
        req.limit = 50;
        final int currentReqId = ++lastReqId;
        reqId = ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                AndroidUtilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (currentReqId == lastReqId) {
                            if (error == null) {
                                FileLog.d("tsupportSearch", "Server search a");
                                TLRPC.TL_contacts_found res = (TLRPC.TL_contacts_found) response;
                                FileLog.d("tsupportSearch", "Server search b");
                                MessagesController.getInstance().globalSearched.addAll(res.users);
                                FileLog.d("tsupportSearch", "Server search c " + res.users.size());
                                lastFoundUsername = query;
                                FileLog.d("tsupportSearch", "Server search d");
                                notifyDataSetChanged();
                            }
                        }
                        reqId = 0;
                    }
                });
            }
        }, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors);
    }
}