package com.pluscubed.plustimer.model;

import android.content.Context;
import android.support.annotation.WorkerThread;
import android.util.Log;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Document;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import rx.Completable;
import rx.Single;
import rx.schedulers.Schedulers;

@JsonAutoDetect(creatorVisibility = JsonAutoDetect.Visibility.NONE,
        fieldVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.NONE
)
public abstract class CbObject {
    private static final ObjectMapper sMapper = new ObjectMapper();
    private static final Map<String, CbObject> sUpdatingObjects = new HashMap<>();
    protected String mId;

    protected CbObject() {
    }

    /**
     * Create new CbObject
     *
     * @throws CouchbaseLiteException
     * @throws IOException
     */
    @WorkerThread
    protected CbObject(Context context) throws CouchbaseLiteException, IOException {
        connectCb(context);
    }

    @WorkerThread
    protected CbObject(Context context, String id) throws CouchbaseLiteException, IOException {
        Log.d("CbObject", id);
        mId = id;
        try {
            CouchbaseInstance.get(context).getDatabase().getDocument(id);
        }catch(Exception e) {
            e.printStackTrace();
        }
        updateCb(context);
    }

    static <T extends CbObject> T fromDocId(Context context, String docId, Class<T> type) throws CouchbaseLiteException, IOException {
        if (sUpdatingObjects.containsKey(docId)) {
            return (T) sUpdatingObjects.get(docId);
        }

        Log.d("asdfasdf", "get update"  + docId);
        try {
            Document doc = CouchbaseInstance.get(context).getDatabase().getDocument(docId);
            return fromDoc(doc, type);
        }catch(Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    static <T extends CbObject> T fromDoc(Document doc, Class<T> type) {
        if (sUpdatingObjects.containsKey(doc.getId())) {
            return (T) sUpdatingObjects.get(doc.getId());
        }

        Map<String, Object> userProperties = doc.getUserProperties();
        userProperties.remove("type");
        T cbObject = sMapper.convertValue(userProperties, type);

        cbObject.mId = doc.getId();

        return cbObject;
    }

    protected void connectCb(Context context) throws CouchbaseLiteException, IOException {

        Log.d("reeee", "connecting" );
        Document doc = CouchbaseInstance.get(context).getDatabase().createDocument();
        mId = doc.getId();
        Log.d("reeee", "connected" + mId );


        updateCb(context);
    }

    protected void updateCb(Context context) {
        Log.d("asdfasdf", "updating" + mId);
        if (mId == null) {
            return;
        }

        sUpdatingObjects.put(mId, this);

        Completable.fromCallable(() -> {
            Document document = getDocument(context);

            document.update(newRevision -> {
                Map<String, Object> userProperties = newRevision.getUserProperties();
                userProperties.putAll(toMap());

                newRevision.setUserProperties(userProperties);
                return true;
            });

            sUpdatingObjects.remove(mId);

            return null;
        }).subscribeOn(Schedulers.io())
                .subscribe();

    }

    public Document getDocument(Context context) throws CouchbaseLiteException, IOException {
        Log.d("asdfasdf", "getdoc" + mId);
        try {
            Document document = CouchbaseInstance.get(context).getDatabase().getDocument(mId);

            return document;
        }catch(Exception e) {
            Log.d("asdfasdf", "caught");
            e.printStackTrace();
            return null;
        }
    }

    public Single<Document> getDocumentDeferred(Context context) {
        return Single.defer(() -> Single.just(getDocument(context)));
    }

    public Map<String, Object> toMap() {
        Map<String, Object> objectMap = sMapper.convertValue(this, Map.class);

        objectMap.put("type", getType());

        return objectMap;
    }

    protected abstract String getType();

}
