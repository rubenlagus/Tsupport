/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.android;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.SparseArray;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.ApplicationLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class ContactsController {

    private Account currentAccount;
    private boolean loadingContacts = false;
    private static final Object loadContactsSync = new Object();
    private boolean ignoreChanges = false;
    private boolean contactsSyncInProgress = false;
    private final Object observerLock = new Object();
    public boolean contactsLoaded = false;
    private boolean contactsBookLoaded = false;
    private String lastContactsVersions = "";
    private ArrayList<Integer> delayedContactsUpdate = new ArrayList<>();
    private String inviteText;
    private boolean updatingInviteText = false;
    private HashMap<String, String> sectionsToReplace = new HashMap<>();

    private int loadingDeleteInfo = 0;
    private int deleteAccountTTL;
    private int loadingLastSeenInfo = 0;
    private ArrayList<TLRPC.PrivacyRule> privacyRules = null;

    public static class Contact {
        public int id;
        public ArrayList<String> phones = new ArrayList<>();
        public ArrayList<String> phoneTypes = new ArrayList<>();
        public ArrayList<String> shortPhones = new ArrayList<>();
        public ArrayList<Integer> phoneDeleted = new ArrayList<>();
        public String first_name;
        public String last_name;
    }

    private String[] projectionPhones = {
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
        ContactsContract.CommonDataKinds.Phone.TYPE,
        ContactsContract.CommonDataKinds.Phone.LABEL
    };
    private String[] projectionNames = {
        ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID,
        ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
        ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,
        ContactsContract.Data.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME
    };

    public HashMap<Integer, Contact> contactsBook = new HashMap<>();
    public HashMap<String, Contact> contactsBookSPhones = new HashMap<>();
    public ArrayList<Contact> phoneBookContacts = new ArrayList<>();

    public ArrayList<TLRPC.TL_contact> contacts = new ArrayList<>();
    public SparseArray<TLRPC.TL_contact> contactsDict = new SparseArray<>();
    public HashMap<String, ArrayList<TLRPC.TL_contact>> usersSectionsDict = new HashMap<>();
    public ArrayList<String> sortedUsersSectionsArray = new ArrayList<>();

    public HashMap<String, TLRPC.TL_contact> contactsByPhone = new HashMap<>();

    private static volatile ContactsController Instance = null;
    public static ContactsController getInstance() {
        ContactsController localInstance = Instance;
        if (localInstance == null) {
            synchronized (ContactsController.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new ContactsController();
                }
            }
        }
        return localInstance;
    }

    public ContactsController() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        if (preferences.getBoolean("needGetStatuses", false)) {
            reloadContactsStatuses();
        }

        sectionsToReplace.put("À", "A");
        sectionsToReplace.put("Á", "A");
        sectionsToReplace.put("Ä", "A");
        sectionsToReplace.put("Ù", "U");
        sectionsToReplace.put("Ú", "U");
        sectionsToReplace.put("Ü", "U");
        sectionsToReplace.put("Ì", "I");
        sectionsToReplace.put("Í", "I");
        sectionsToReplace.put("Ï", "I");
        sectionsToReplace.put("È", "E");
        sectionsToReplace.put("É", "E");
        sectionsToReplace.put("Ê", "E");
        sectionsToReplace.put("Ë", "E");
        sectionsToReplace.put("Ò", "O");
        sectionsToReplace.put("Ó", "O");
        sectionsToReplace.put("Ö", "O");
        sectionsToReplace.put("Ç", "C");
        sectionsToReplace.put("Ñ", "N");
        sectionsToReplace.put("Ÿ", "Y");
        sectionsToReplace.put("Ý", "Y");
        sectionsToReplace.put("Ţ", "Y");
    }

    public void cleanup() {
        contactsBook.clear();
        contactsBookSPhones.clear();
        phoneBookContacts.clear();
        contacts.clear();
        contactsDict.clear();
        usersSectionsDict.clear();
        sortedUsersSectionsArray.clear();
        delayedContactsUpdate.clear();
        contactsByPhone.clear();

        loadingContacts = false;
        contactsSyncInProgress = false;
        contactsLoaded = false;
        contactsBookLoaded = false;
        lastContactsVersions = "";
        loadingDeleteInfo = 0;
        deleteAccountTTL = 0;
        loadingLastSeenInfo = 0;
        privacyRules = null;
    }

    public void checkInviteText() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        inviteText = preferences.getString("invitetext", null);
        int time = preferences.getInt("invitetexttime", 0);
        if (!updatingInviteText && (inviteText == null || time + 86400 < (int)(System.currentTimeMillis() / 1000))) {
            updatingInviteText = true;
            TLRPC.TL_help_getInviteText req = new TLRPC.TL_help_getInviteText();
            req.lang_code = LocaleController.getLocaleString(LocaleController.getInstance().getSystemDefaultLocale());
            if (req.lang_code == null || req.lang_code.length() == 0) {
                req.lang_code = "en";
            }
            ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        final TLRPC.TL_help_inviteText res = (TLRPC.TL_help_inviteText)response;
                        if (res.message.length() != 0) {
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    updatingInviteText = false;
                                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                                    SharedPreferences.Editor editor = preferences.edit();
                                    editor.putString("invitetext", res.message);
                                    editor.putInt("invitetexttime", (int) (System.currentTimeMillis() / 1000));
                                    editor.commit();
                                }
                            });
                        }
                    }
                }
            }, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors);
        }
    }

    public String getInviteText() {
        return inviteText != null ? inviteText : LocaleController.getString("InviteText", R.string.InviteText);
    }

    public void checkAppAccount() {
        AccountManager am = AccountManager.get(ApplicationLoader.applicationContext);
        Account[] accounts;
        try {
            accounts = am.getAccountsByType("org.telegram.account");
            if (accounts != null && accounts.length > 0) {
                for (Account c : accounts) {
                    am.removeAccount(c, null, null);
                }
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }

        accounts = am.getAccountsByType("org.telegram.tsupport");
        boolean recreateAccount = false;
        if (UserConfig.isClientActivated()) {
            if (accounts.length == 1) {
                Account acc = accounts[0];
                if (!acc.name.equals(UserConfig.getCurrentUser().phone)) {
                    recreateAccount = true;
                } else {
                    currentAccount = acc;
                }
            } else {
                recreateAccount = true;
            }
            readContacts();
        } else {
            if (accounts.length > 0) {
                recreateAccount = true;
            }
        }
        if (recreateAccount) {
            for (Account c : accounts) {
                am.removeAccount(c, null, null);
            }
            if (UserConfig.isClientActivated()) {
                try {
                    currentAccount = new Account(UserConfig.getCurrentUser().phone, "org.telegram.tsupport");
                    am.addAccountExplicitly(currentAccount, "", null);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        }
    }

    public void deleteAllAppAccounts() {
        try {
            AccountManager am = AccountManager.get(ApplicationLoader.applicationContext);
            Account[] accounts = am.getAccountsByType("org.telegram.tsupport");
            for (Account c : accounts) {
                am.removeAccount(c, null, null);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void checkContacts() {
        Utilities.globalQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (checkContactsInternal()) {
                    FileLog.e("tmessages", "detected contacts change");
                    ContactsController.getInstance().performSyncPhoneBook(ContactsController.getInstance().getContactsCopy(ContactsController.getInstance().contactsBook), true, false, true);
                }
            }
        });
    }

    private boolean checkContactsInternal() {
        return false;
        // Disabled
    }

    public void readContacts() {
        synchronized (loadContactsSync) {
            if (loadingContacts) {
                return;
            }
            loadingContacts = true;
        }

        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (!contacts.isEmpty() || contactsLoaded) {
                    synchronized (loadContactsSync) {
                        loadingContacts = false;
                    }
                    return;
                }
                loadContacts(true, false);
            }
        });
    }

    private HashMap<Integer, Contact> readContactsFromPhoneBook() {
        HashMap<Integer, Contact> contactsMap = new HashMap<>();
        try {
            ContentResolver cr = ApplicationLoader.applicationContext.getContentResolver();

            HashMap<String, Contact> shortContacts = new HashMap<>();
            ArrayList<Integer> idsArr = new ArrayList<>();
            Cursor pCur = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projectionPhones, null, null, null);
            if (pCur != null) {
                if (pCur.getCount() > 0) {
                    while (pCur.moveToNext()) {
                        String number = pCur.getString(1);
                        if (number == null || number.length() == 0) {
                            continue;
                        }
                        number = PhoneFormat.stripExceptNumbers(number, true);
                        if (number.length() == 0) {
                            continue;
                        }

                        String shortNumber = number;

                        if (number.startsWith("+")) {
                            shortNumber = number.substring(1);
                        }

                        if (shortContacts.containsKey(shortNumber)) {
                            continue;
                        }

                        Integer id = pCur.getInt(0);
                        if (!idsArr.contains(id)) {
                            idsArr.add(id);
                        }

                        int type = pCur.getInt(2);
                        Contact contact = contactsMap.get(id);
                        if (contact == null) {
                            contact = new Contact();
                            contact.first_name = "";
                            contact.last_name = "";
                            contact.id = id;
                            contactsMap.put(id, contact);
                        }

                        contact.shortPhones.add(shortNumber);
                        contact.phones.add(number);
                        contact.phoneDeleted.add(0);

                        if (type == ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM) {
                            contact.phoneTypes.add(pCur.getString(3));
                        } else if (type == ContactsContract.CommonDataKinds.Phone.TYPE_HOME) {
                            contact.phoneTypes.add(LocaleController.getString("PhoneHome", R.string.PhoneHome));
                        } else if (type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE) {
                            contact.phoneTypes.add(LocaleController.getString("PhoneMobile", R.string.PhoneMobile));
                        } else if (type == ContactsContract.CommonDataKinds.Phone.TYPE_WORK) {
                            contact.phoneTypes.add(LocaleController.getString("PhoneWork", R.string.PhoneWork));
                        } else if (type == ContactsContract.CommonDataKinds.Phone.TYPE_MAIN) {
                            contact.phoneTypes.add(LocaleController.getString("PhoneMain", R.string.PhoneMain));
                        } else {
                            contact.phoneTypes.add(LocaleController.getString("PhoneOther", R.string.PhoneOther));
                        }
                        shortContacts.put(shortNumber, contact);
                    }
                }
                pCur.close();
            }
            String ids = TextUtils.join(",", idsArr);

            pCur = cr.query(ContactsContract.Data.CONTENT_URI, projectionNames, ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID + " IN (" + ids + ") AND " + ContactsContract.Data.MIMETYPE + " = '" + ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE + "'", null, null);
            if (pCur != null && pCur.getCount() > 0) {
                while (pCur.moveToNext()) {
                    int id = pCur.getInt(0);
                    String fname = pCur.getString(1);
                    String sname = pCur.getString(2);
                    String sname2 = pCur.getString(3);
                    String mname = pCur.getString(4);
                    Contact contact = contactsMap.get(id);
                    if (contact != null && contact.first_name.length() == 0 && contact.last_name.length() == 0) {
                        contact.first_name = fname;
                        contact.last_name = sname;
                        if (contact.first_name == null) {
                            contact.first_name = "";
                        }
                        if (mname != null && mname.length() != 0) {
                            if (contact.first_name.length() != 0) {
                                contact.first_name += " " + mname;
                            } else {
                                contact.first_name = mname;
                            }
                        }
                        if (contact.last_name == null) {
                            contact.last_name = "";
                        }
                        if (contact.last_name.length() == 0 && contact.first_name.length() == 0 && sname2 != null && sname2.length() != 0) {
                            contact.first_name = sname2;
                        }
                    }
                }
                pCur.close();
            }

            try {
                pCur = cr.query(ContactsContract.RawContacts.CONTENT_URI, new String[] { "display_name", ContactsContract.RawContacts.SYNC1, ContactsContract.RawContacts.CONTACT_ID }, ContactsContract.RawContacts.ACCOUNT_TYPE + " = " + "'com.whatsapp'", null, null);
                if (pCur != null) {
                    while ((pCur.moveToNext())) {
                        String phone = pCur.getString(1);
                        if (phone == null || phone.length() == 0) {
                            continue;
                        }
                        boolean withPlus = phone.startsWith("+");
                        phone = Utilities.parseIntToString(phone);
                        if (phone == null || phone.length() == 0) {
                            continue;
                        }
                        String shortPhone = phone;
                        if (!withPlus) {
                            phone = "+" + phone;
                        }

                        if (shortContacts.containsKey(shortPhone)) {
                            continue;
                        }

                        String name = pCur.getString(0);
                        if (name == null || name.length() == 0) {
                            name = PhoneFormat.getInstance().format(phone);
                        }

                        Contact contact = new Contact();
                        contact.first_name = name;
                        contact.last_name = "";
                        contact.id = pCur.getInt(2);
                        contactsMap.put(contact.id, contact);

                        contact.phoneDeleted.add(0);
                        contact.shortPhones.add(shortPhone);
                        contact.phones.add(phone);
                        contact.phoneTypes.add(LocaleController.getString("PhoneMobile", R.string.PhoneMobile));
                        shortContacts.put(shortPhone, contact);
                    }
                    pCur.close();
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            contactsMap.clear();
        }
        if (BuildVars.DEBUG_VERSION) {
            for (HashMap.Entry<Integer, Contact> entry : contactsMap.entrySet()) {
                Contact contact = entry.getValue();
                FileLog.e("tmessages", "contact = " + contact.first_name + " " + contact.last_name);
                if (contact.first_name.length() == 0 && contact.last_name.length() == 0 && contact.phones.size() > 0) {
                    FileLog.e("tmessages", "warning, empty name for contact = " + contact.id);
                }
                FileLog.e("tmessages", "phones:");
                for (String s : contact.phones) {
                    FileLog.e("tmessages", "phone = " + s);
                }
                FileLog.e("tmessages", "short phones:");
                for (String s : contact.shortPhones) {
                    FileLog.e("tmessages", "short phone = " + s);
                }
            }
        }
        return contactsMap;
    }

    public HashMap<Integer, Contact> getContactsCopy(HashMap<Integer, Contact> original) {
        HashMap<Integer, Contact> ret = new HashMap<>();
        for (HashMap.Entry<Integer, Contact> entry : original.entrySet()) {
            Contact copyContact = new Contact();
            Contact originalContact = entry.getValue();
            copyContact.phoneDeleted.addAll(originalContact.phoneDeleted);
            copyContact.phones.addAll(originalContact.phones);
            copyContact.phoneTypes.addAll(originalContact.phoneTypes);
            copyContact.shortPhones.addAll(originalContact.shortPhones);
            copyContact.first_name = originalContact.first_name;
            copyContact.last_name = originalContact.last_name;
            copyContact.id = originalContact.id;
            ret.put(copyContact.id, copyContact);
        }
        return ret;
    }

    public void performSyncPhoneBook(final HashMap<Integer, Contact> contactHashMap, final boolean requ, final boolean first, final boolean schedule) {
        // Disabled
    }

    public boolean isLoadingContacts() {
        synchronized (loadContactsSync) {
            return loadingContacts;
        }
    }

    public void loadContacts(boolean fromCache, boolean cacheEmpty) {
        synchronized (loadContactsSync) {
            loadingContacts = true;
        }
        if (fromCache) {
            FileLog.e("tmessages", "load contacts from cache");
            MessagesStorage.getInstance().getContacts();
        } else {
            FileLog.e("tmessages", "load contacts from server");
            TLRPC.TL_contacts_getContacts req = new TLRPC.TL_contacts_getContacts();
            req.hash = cacheEmpty ? "" : UserConfig.contactsHash;
            ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        TLRPC.contacts_Contacts res = (TLRPC.contacts_Contacts)response;
                        if (res instanceof TLRPC.TL_contacts_contactsNotModified) {
                            contactsLoaded = true;
                            if (!delayedContactsUpdate.isEmpty() && contactsLoaded && contactsBookLoaded) {
                                applyContactsUpdates(delayedContactsUpdate, null, null, null);
                                delayedContactsUpdate.clear();
                            }
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    synchronized (loadContactsSync) {
                                        loadingContacts = false;
                                    }
                                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.contactsDidLoaded);
                                }
                            });
                            FileLog.e("tmessages", "load contacts don't change");
                            return;
                        }
                        processLoadedContacts(res.contacts, res.users, 0);
                    }
                }
            }, true, RPCRequest.RPCRequestClassGeneric);
        }
    }

    public void processLoadedContacts(final ArrayList<TLRPC.TL_contact> contactsArr, final ArrayList<TLRPC.User> usersArr, final int from) {
        //from: 0 - from server, 1 - from db, 2 - from imported contacts
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                MessagesController.getInstance().putUsers(usersArr, from == 1);

                final HashMap<Integer, TLRPC.User> usersDict = new HashMap<>();

                final boolean isEmpty = contactsArr.isEmpty();

                if (!contacts.isEmpty()) {
                    for (int a = 0; a < contactsArr.size(); a++) {
                        TLRPC.TL_contact contact = contactsArr.get(a);
                        if (contactsDict.get(contact.user_id) != null) {
                            contactsArr.remove(a);
                            a--;
                        }
                    }
                    contactsArr.addAll(contacts);
                }

                for (TLRPC.TL_contact contact : contactsArr) {
                    TLRPC.User user = MessagesController.getInstance().getUser(contact.user_id);
                    if (user != null) {
                        usersDict.put(user.id, user);

                        if (BuildVars.DEBUG_VERSION) {
                            FileLog.e("tmessages", "loaded user contact " + user.first_name + " " + user.last_name + " " + user.phone);
                        }
                    }
                }

                Utilities.stageQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        FileLog.e("tmessages", "done loading contacts");
                        if (from == 1 && contactsArr.isEmpty()) {
                            loadContacts(false, true);
                            return;
                        }

                        for (TLRPC.TL_contact contact : contactsArr) {
                            if (usersDict.get(contact.user_id) == null && contact.user_id != UserConfig.getClientUserId()) {
                                loadContacts(false, true);
                                FileLog.e("tmessages", "contacts are broken, load from server");
                                return;
                            }
                        }

                        if (from != 1) {
                            MessagesStorage.getInstance().putUsersAndChats(usersArr, null, true, true);
                            MessagesStorage.getInstance().putContacts(contactsArr, from != 2);
                            Collections.sort(contactsArr, new Comparator<TLRPC.TL_contact>() {
                                @Override
                                public int compare(TLRPC.TL_contact tl_contact, TLRPC.TL_contact tl_contact2) {
                                    if (tl_contact.user_id > tl_contact2.user_id) {
                                        return 1;
                                    } else if (tl_contact.user_id < tl_contact2.user_id) {
                                        return -1;
                                    }
                                    return 0;
                                }
                            });
                            StringBuilder ids = new StringBuilder();
                            for (TLRPC.TL_contact aContactsArr : contactsArr) {
                                if (ids.length() != 0) {
                                    ids.append(",");
                                }
                                ids.append(aContactsArr.user_id);
                            }
                            UserConfig.contactsHash = Utilities.MD5(ids.toString());
                            UserConfig.saveConfig(false);
                        }

                        Collections.sort(contactsArr, new Comparator<TLRPC.TL_contact>() {
                            @Override
                            public int compare(TLRPC.TL_contact tl_contact, TLRPC.TL_contact tl_contact2) {
                                TLRPC.User user1 = usersDict.get(tl_contact.user_id);
                                TLRPC.User user2 = usersDict.get(tl_contact2.user_id);
                                String name1 = UserObject.getFirstName(user1);
                                String name2 = UserObject.getFirstName(user2);
                                return name1.compareTo(name2);
                            }
                        });

                        final SparseArray<TLRPC.TL_contact> contactsDictionary = new SparseArray<>();
                        final HashMap<String, ArrayList<TLRPC.TL_contact>> sectionsDict = new HashMap<>();
                        final ArrayList<String> sortedSectionsArray = new ArrayList<>();
                        HashMap<String, TLRPC.TL_contact> contactsByPhonesDict = null;

                        if (!contactsBookLoaded) {
                            contactsByPhonesDict = new HashMap<>();
                        }

                        final HashMap<String, TLRPC.TL_contact> contactsByPhonesDictFinal = contactsByPhonesDict;

                        for (TLRPC.TL_contact value : contactsArr) {
                            TLRPC.User user = usersDict.get(value.user_id);
                            if (user == null) {
                                continue;
                            }
                            contactsDictionary.put(value.user_id, value);
                            if (contactsByPhonesDict != null) {
                                contactsByPhonesDict.put(user.phone, value);
                            }

                            String key = UserObject.getFirstName(user);
                            if (key.length() > 1) {
                                key = key.substring(0, 1);
                            }
                            if (key.length() == 0) {
                                key = "#";
                            } else {
                                key = key.toUpperCase();
                            }
                            String replace = sectionsToReplace.get(key);
                            if (replace != null) {
                                key = replace;
                            }
                            ArrayList<TLRPC.TL_contact> arr = sectionsDict.get(key);
                            if (arr == null) {
                                arr = new ArrayList<>();
                                sectionsDict.put(key, arr);
                                sortedSectionsArray.add(key);
                            }
                            arr.add(value);
                        }

                        Collections.sort(sortedSectionsArray, new Comparator<String>() {
                            @Override
                            public int compare(String s, String s2) {
                                char cv1 = s.charAt(0);
                                char cv2 = s2.charAt(0);
                                if (cv1 == '#') {
                                    return 1;
                                } else if (cv2 == '#') {
                                    return -1;
                                }
                                return s.compareTo(s2);
                            }
                        });

                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                contacts = contactsArr;
                                contactsDict = contactsDictionary;
                                usersSectionsDict = sectionsDict;
                                sortedUsersSectionsArray = sortedSectionsArray;
                                if (from != 2) {
                                    synchronized (loadContactsSync) {
                                        loadingContacts = false;
                                    }
                                }
                                performWriteContactsToPhoneBook();
                                updateUnregisteredContacts(contactsArr);

                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.contactsDidLoaded);

                                if (from != 1 && !isEmpty) {
                                    saveContactsLoadTime();
                                } else {
                                    reloadContactsStatusesMaybe();
                                }
                            }
                        });

                        if (!delayedContactsUpdate.isEmpty() && contactsLoaded && contactsBookLoaded) {
                            applyContactsUpdates(delayedContactsUpdate, null, null, null);
                            delayedContactsUpdate.clear();
                        }

                        if (contactsByPhonesDictFinal != null) {
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    Utilities.globalQueue.postRunnable(new Runnable() {
                                        @Override
                                        public void run() {
                                            contactsByPhone = contactsByPhonesDictFinal;
                                        }
                                    });
                                    if (contactsSyncInProgress) {
                                        return;
                                    }
                                    contactsSyncInProgress = true;
                                    MessagesStorage.getInstance().getCachedPhoneBook();
                                }
                            });
                        } else {
                            contactsLoaded = true;
                        }
                    }
                });
            }
        });
    }

    private void reloadContactsStatusesMaybe() {
        try {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            long lastReloadStatusTime = preferences.getLong("lastReloadStatusTime", 0);
            if (lastReloadStatusTime < System.currentTimeMillis() - 1000 * 60 * 60 * 24) {
                reloadContactsStatuses();
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    private void saveContactsLoadTime() {
        try {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            preferences.edit().putLong("lastReloadStatusTime", System.currentTimeMillis()).commit();
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    private void updateUnregisteredContacts(final ArrayList<TLRPC.TL_contact> contactsArr) {
        // Disabled
    }

    private void buildContactsSectionsArrays(boolean sort) {
        // Disabled
    }

    private void performWriteContactsToPhoneBookInternal(ArrayList<TLRPC.TL_contact> contactsArray) {
        // Disabled
    }

    private void performWriteContactsToPhoneBook() {
        // Disabled
    }

    private void applyContactsUpdates(ArrayList<Integer> ids, ConcurrentHashMap<Integer, TLRPC.User> userDict, ArrayList<TLRPC.TL_contact> newC, ArrayList<Integer> contactsTD) {
        // Disabled
    }

    public void processContactsUpdates(ArrayList<Integer> ids, ConcurrentHashMap<Integer, TLRPC.User> userDict) {
        // Disabled
    }

    public long addContactToPhoneBook(TLRPC.User user, boolean check) {
        return 0L;
        // Disabledg
    }

    private void deleteContactFromPhoneBook(int uid) {
        // Disabled
    }

    protected void markAsContacted(final String contactId) {
        // Disabled
    }

    public void addContact(TLRPC.User user) {
        // Disabled
    }

    public void deleteContact(final ArrayList<TLRPC.User> users) {
        // Disabled
    }

    public void reloadContactsStatuses() {
        saveContactsLoadTime();
        MessagesController.getInstance().clearFullUsers();
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        final SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("needGetStatuses", true).commit();
        TLRPC.TL_contacts_getStatuses req = new TLRPC.TL_contacts_getStatuses();
        ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(final TLObject response, TLRPC.TL_error error) {
                if (error == null) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            editor.remove("needGetStatuses").commit();
                            TLRPC.Vector vector = (TLRPC.Vector) response;
                            if (!vector.objects.isEmpty()) {
                                ArrayList<TLRPC.User> dbUsersStatus = new ArrayList<>();
                                for (Object object : vector.objects) {
                                    TLRPC.User toDbUser = new TLRPC.User();
                                    TLRPC.TL_contactStatus status = (TLRPC.TL_contactStatus) object;

                                    if (status == null) {
                                        continue;
                                    }
                                    if (status.status instanceof TLRPC.TL_userStatusRecently) {
                                        status.status.expires = -100;
                                    } else if (status.status instanceof TLRPC.TL_userStatusLastWeek) {
                                        status.status.expires = -101;
                                    } else if (status.status instanceof TLRPC.TL_userStatusLastMonth) {
                                        status.status.expires = -102;
                                    }

                                    TLRPC.User user = MessagesController.getInstance().getUser(status.user_id);
                                    if (user != null) {
                                        user.status = status.status;
                                    }
                                    toDbUser.status = status.status;
                                    dbUsersStatus.add(toDbUser);
                                }
                                MessagesStorage.getInstance().updateUsers(dbUsersStatus, true, true, true);
                            }
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_STATUS);
                        }
                    });
                }
            }
        });
    }

    public void loadPrivacySettings() {
        if (loadingDeleteInfo == 0) {
            loadingDeleteInfo = 1;
            TLRPC.TL_account_getAccountTTL req = new TLRPC.TL_account_getAccountTTL();
            ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(final TLObject response, final TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (error == null) {
                                TLRPC.TL_accountDaysTTL ttl = (TLRPC.TL_accountDaysTTL) response;
                                deleteAccountTTL = ttl.days;
                                loadingDeleteInfo = 2;
                            } else {
                                loadingDeleteInfo = 0;
                            }
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.privacyRulesUpdated);
                        }
                    });
                }
            });
        }
        if (loadingLastSeenInfo == 0) {
            loadingLastSeenInfo = 1;
            TLRPC.TL_account_getPrivacy req = new TLRPC.TL_account_getPrivacy();
            req.key = new TLRPC.TL_inputPrivacyKeyStatusTimestamp();
            ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(final TLObject response, final TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (error == null) {
                                TLRPC.TL_account_privacyRules rules = (TLRPC.TL_account_privacyRules) response;
                                MessagesController.getInstance().putUsers(rules.users, false);
                                privacyRules = rules.rules;
                                loadingLastSeenInfo = 2;
                            } else {
                                loadingLastSeenInfo = 0;
                            }
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.privacyRulesUpdated);
                        }
                    });
                }
            });
        }
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.privacyRulesUpdated);
    }

    public void setDeleteAccountTTL(int ttl) {
        deleteAccountTTL = ttl;
    }

    public int getDeleteAccountTTL() {
        return deleteAccountTTL;
    }

    public boolean getLoadingDeleteInfo() {
        return loadingDeleteInfo != 2;
    }

    public boolean getLoadingLastSeenInfo() {
        return loadingLastSeenInfo != 2;
    }

    public ArrayList<TLRPC.PrivacyRule> getPrivacyRules() {
        return privacyRules;
    }

    public void setPrivacyRules(ArrayList<TLRPC.PrivacyRule> rules) {
        privacyRules = rules;
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.privacyRulesUpdated);
        reloadContactsStatuses();
    }

    public static String formatName(String firstName, String lastName) {
        /*if ((firstName == null || firstName.length() == 0) && (lastName == null || lastName.length() == 0)) {
            return LocaleController.getString("HiddenName", R.string.HiddenName);
        }*/
        if (firstName != null) {
            firstName = firstName.trim();
        }
        if (lastName != null) {
            lastName = lastName.trim();
        }
        StringBuilder result = new StringBuilder((firstName != null ? firstName.length() : 0) + (lastName != null ? lastName.length() : 0) + 1);
        if (LocaleController.nameDisplayOrder == 1) {
            if (firstName != null && firstName.length() > 0) {
                result.append(firstName);
                if (lastName != null && lastName.length() > 0) {
                    result.append(" ");
                    result.append(lastName);
                }
            } else if (lastName != null && lastName.length() > 0) {
                result.append(lastName);
            }
        } else {
            if (lastName != null && lastName.length() > 0) {
                result.append(lastName);
                if (firstName != null && firstName.length() > 0) {
                    result.append(" ");
                    result.append(firstName);
                }
            } else if (firstName != null && firstName.length() > 0) {
                result.append(firstName);
            }
        }
        return result.toString();
    }
}
