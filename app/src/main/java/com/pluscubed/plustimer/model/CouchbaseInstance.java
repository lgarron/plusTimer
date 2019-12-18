package com.pluscubed.plustimer.model;

import android.content.Context;
import android.util.Log;

import com.auth0.api.authentication.AuthenticationAPIClient;
import com.auth0.api.callback.BaseCallback;
import com.auth0.api.callback.RefreshIdTokenCallback;
import com.auth0.core.Auth0;
import com.auth0.core.UserProfile;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.DatabaseOptions;
import com.couchbase.lite.Manager;
import com.couchbase.lite.QueryOptions;
import com.couchbase.lite.android.AndroidContext;
import com.couchbase.lite.replicator.Replication;
import com.pluscubed.plustimer.R;
import com.pluscubed.plustimer.utils.PrefUtils;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;

import rx.Single;
import rx.schedulers.Schedulers;

public class CouchbaseInstance {

    public static final String DATABASE_URL = "https://apollo:apollo@couchdb.api.cubing.net/";
  //  public static final String DATABASE_URL = "https://ajkldjasdsald.com:5984/";
    private static final String DB_SOLVES = "db_solves";
    private static CouchbaseInstance sCouchbaseInstance;
    private Context mContext;
    private Database mDatabase;

    private AuthenticationAPIClient mAuthenticationApiClient;
    private Auth0 mAuth0;

    private UserProfile mUser;
    private String mIdToken;

    private Replication mPush;
    private Replication mPull;

    private CouchbaseInstance(Context context) throws CouchbaseLiteException, IOException {
        mContext = context.getApplicationContext();

        DatabaseOptions options = new DatabaseOptions();
        options.setCreate(true);
        Manager manager = new Manager(new AndroidContext(mContext), Manager.DEFAULT_OPTIONS);
        mDatabase = manager.openDatabase(DB_SOLVES, options);

        initApiClient();

        Log.d("REEEEE", "couchbase instance created" + mDatabase.getAllDocs(new QueryOptions()).keySet());
    }

    public static CouchbaseInstance get(Context context) throws CouchbaseLiteException, IOException {
        if (sCouchbaseInstance == null) {
            sCouchbaseInstance = new CouchbaseInstance(context);
        }
        return sCouchbaseInstance;
    }

    public static Single<CouchbaseInstance> getDeferred(Context context) {
        return Single.defer(() -> Single.just(get(context)));
    }

    public Database getDatabase() {
        return mDatabase;
    }

    public Single<UserProfile> getLoggedInUser() {
        return Single.defer(() -> {
            if (mUser == null) {
                String loginData = PrefUtils.getLoginData(mContext);

                if (loginData != null) {
                    String[] data = loginData.split("\\s+");
                    mIdToken = data[0];
                    String refreshToken = data[1];
                    int expire = Integer.parseInt(data[2]);
                    if (System.currentTimeMillis() / 1000 > expire) {
                        //Expired ID token
                        return getIdTokenFromRefreshToken(refreshToken)
                                .flatMap(this::loadUserFromIdToken);
                    } else {
                        return loadUserFromIdToken(mIdToken);
                    }
                } else {
                    return Single.just(null);
                }
            } else {
                return Single.just(mUser);
            }
        });
    }

    public Single<UserProfile> signIn(String idToken, String refreshToken) {
        mIdToken = idToken;
        //Get expiration time and save tokens
        getIdTokenFromRefreshToken(refreshToken).subscribe();
        return loadUserFromIdToken(idToken);
    }

    private Single<String> getIdTokenFromRefreshToken(String refreshToken) {
        return Single.create((Single.OnSubscribe<String>) singleSubscriber ->
                mAuthenticationApiClient.delegationWithRefreshToken(refreshToken)
                        .start(new RefreshIdTokenCallback() {
                            @Override
                            public void onSuccess(String idToken, String tokenType, int expiresIn) {
                                saveTokens(idToken, refreshToken, expiresIn);

                                Single.just(idToken);
                            }

                            @Override
                            public void onFailure(Throwable error) {
                                Single.error(error);
                            }
                        })).subscribeOn(Schedulers.io());

    }

    private Single<UserProfile> loadUserFromIdToken(String idToken) {
        return Single.create((Single.OnSubscribe<UserProfile>) singleSubscriber ->
                mAuthenticationApiClient.tokenInfo(idToken).start(new BaseCallback<UserProfile>() {
                    @Override
                    public void onSuccess(UserProfile payload) {
                        singleSubscriber.onSuccess(payload);
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        singleSubscriber.onError(error);
                    }
                })).subscribeOn(Schedulers.io())
                .doOnSuccess(userProfile -> mUser = userProfile);
    }

    public Auth0 getAuth0() {
        return mAuth0;
    }

    private void initApiClient() {
        if (mAuth0 == null || mAuthenticationApiClient == null) {
            String clientId = mContext.getString(R.string.auth0_client_id);
            String domain = mContext.getString(R.string.auth0_domain_name);
            mAuth0 = new Auth0(clientId, domain);
            mAuthenticationApiClient = mAuth0.newAuthenticationAPIClient();
        }
    }


    private void saveTokens(String idToken, String refreshToken, int expiresIn) {
        int expireTimestamp = (int) (System.currentTimeMillis() / 1000L + expiresIn);
        PrefUtils.setLoginData(mContext, idToken + " " + refreshToken + " " + expireTimestamp);
    }

    public void startReplication() {
        stopReplication();

        Log.d("rererer", "start replication");

//        String userId = mUser.getId();
        String database = "results-apollo";

        URL url = null;
        try {
            url = new URL(DATABASE_URL + database);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        mPush = getDatabase().createPushReplication(url);
        mPull = getDatabase().createPullReplication(url);
        mPush.setContinuous(true);
        mPull.setContinuous(true);
        HashMap<String, Object> requestHeadersParam = new HashMap<>();
//                requestHeadersParam.put("Authorization", "Bearer " + mIdToken);
        mPush.setHeaders(requestHeadersParam);
        mPull.setHeaders(requestHeadersParam);
        mPush.start();
        mPull.start();
        mPull.addChangeListener(event -> {
        });
//
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(DATABASE_URL)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {

            }

            @Override
            public void onResponse(Response response) throws IOException {

            }
        });
    }

    public void stopReplication() {
        if (mPull != null && mPush != null) {
            mPush.stop();
            mPull.stop();
            mPush = null;
            mPull = null;
        }
    }
}
