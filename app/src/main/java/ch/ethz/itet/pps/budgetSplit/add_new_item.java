package ch.ethz.itet.pps.budgetSplit;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.LoaderManager;
import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.CursorJoiner;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;


import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;
import java.util.zip.Inflater;

import ch.ethz.itet.pps.budgetSplit.contentProvider.budgetSplitContract;


public class add_new_item extends Activity implements LoaderManager.LoaderCallbacks<Cursor> {
    static final String LOG_TAG = "addNewItemActivity";

    public static final String EXTRA_ITEM_DETAILS_URI = "itemUri";
    public static final String EXTRA_PROJECT_DETAILS_URI = "projectUri";

    //Define integer constants to identify the individual loaders.
    private static final int LOADER_ITEM = 0;
    private boolean loaderItemFinishedInitialLoading = false;
    private static final int LOADER_PARTICIPANTS = 1;
    private boolean loaderParticipantsFinishedInitialLoading = false;
    private static final int LOADER_ITEM_PARTICIPANTS = 2;
    private boolean loaderItemParticipantsFinishedInitialLoading = false;
    private static final int LOADER_ITEM_TAGS = 3;
    private boolean loaderItemTagsFinishedInitialLoading = false;
    private static final int LOADER_TAGS = 4;
    private boolean loaderTagsFinishedInitialLoading = false;
    private static final int LOADER_CURRENCIES = 5;
    private boolean loaderCurrenciesFinishedInitialLoading = false;
    private static final int LOADER_PROJECT = 6;
    private boolean loaderProjectFinishedInitialLoading = false;
    private static final int LOADER_EXCLUDE_ITEMS = 7;
    private boolean loaderExcludeItemsFinishedInitialLoading = false;

    //Project- and Item-specific Variables
    boolean isNewItem = true;
    Uri itemUri;
    Uri projectUri;
    long itemId = -1;
    long projectId;
    long myParticipantsId;
    String myUniqueId;
    long defaultCurrencyId;
    String defaultCurrencyCode;
    Double defaultCurrencyExchangeRate;


    //Cursor Variables
    Cursor cursorItem = null;
    Cursor cursorItemParticipants = null;
    Cursor cursorParticipants = null;
    Cursor cursorItemTags = null;
    Cursor cursorTags = null;
    Cursor cursorProject = null;
    Cursor cursorCurrencies = null;
    Cursor cursorExcludeItems = null;

    //View variables
    ListView payersList;
    ListView excludeList;
    EditText itemNameEditText;

    //Tags Variables
    LinearLayout tagsAddedLayout;
    List<View> tagsAddedViews;
    List<View> tagsNotAddedViews;
    Stack<View> tagViewPool;
    Stack<TextView> tagTextPool;
    List<Tag> tagsAlreadyAdded;
    List<Tag> tagsToAdd;
    List<Tag> tagsToDelete;
    List<Tag> tagsNotAdded;
    LinearLayout tagChooserList;
    ImageButton addNewTagButton;

    PopupWindow tagChooserPopup;
    AlertDialog tagCreatePopup;

    //Payer Variables
    List<Payer> payersAlreadyAdded;
    List<Payer> payersToAdd;
    List<Payer> payersToDelete;
    List<Payer> payersToUpdate;
    ItemParticipantsAdapter payerAdapter;
    AlertDialog payerChooserPopup;
    ImageButton addNewPayerButton;

    //Exclude Items Variables
    List<ExcludeItem> excludeItemsAlreadyAdded;
    List<ExcludeItem> excludeItemsToAdd;
    List<ExcludeItem> excludeItemsToDelete;
    List<ExcludeItem> excludeItemsToUpdate;
    ExcludeItemsAdapter excludeItemsAdapter;
    AlertDialog excludeItemsChooserPopup;
    ImageButton excludeSomeoneButton;

    //Variable used to restrict full privileges for non-Creating users.
    boolean fullAccess = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_new_item);
        //Try to get ItemUri.
        itemUri = getIntent().getParcelableExtra(EXTRA_ITEM_DETAILS_URI);
        projectUri = getIntent().getParcelableExtra(EXTRA_PROJECT_DETAILS_URI);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        myParticipantsId = preferences.getLong(getString(R.string.pref_user_id), -1);

        //Check if a valid Project Uri was added to intent.
        if (projectUri != null) {
            if (!projectUri.getAuthority().equals(budgetSplitContract.AUTHORITY)) {
                throw new IllegalArgumentException("The Project uri added to intent didn't match the budgetSplit Authority.");
            }
            if (!projectUri.getPathSegments().get(0).equals(budgetSplitContract.projectsDetailsRO.CONTENT_URI.getLastPathSegment())) {
                throw new IllegalArgumentException("The Project Uri added to intent didn't match the project table.");
            }
            try {
                projectId = ContentUris.parseId(projectUri);
            } catch (UnsupportedOperationException e) {
                throw new IllegalArgumentException("The project Uri added to intent wasn't a single row URI.", e);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("The project Uri added to intent wasn't a single row URI.", e);
            }
        } else {
            throw new IllegalArgumentException("There was no current Project Uri added to intent.");
        }

        //Check if a valid Item-Content-Uri was added to intent. If a valid item-URI is present, the activity will load the item into edit-mode.
        if (itemUri != null) {
            if (!itemUri.getAuthority().equals(budgetSplitContract.AUTHORITY)) {
                throw new IllegalArgumentException("The Item Uri added to intent didn't match the budgetSplit Authority.");
            }
            if (!itemUri.getPathSegments().get(0).equals(budgetSplitContract.itemsDetailsRO.CONTENT_URI.getLastPathSegment())) {
                throw new IllegalArgumentException("The Uri added to intent didn't match the item table.");
            }
            try {
                itemId = ContentUris.parseId(itemUri);
            } catch (UnsupportedOperationException e) {
                throw new IllegalArgumentException("The item Uri added to intent wasn't a single row URI.", e);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("The item Uri added to intent wasn't a single row URI.", e);
            }
            isNewItem = false;
            getLoaderManager().initLoader(LOADER_ITEM, null, this);
            getLoaderManager().initLoader(LOADER_ITEM_PARTICIPANTS, null, this);
            getLoaderManager().initLoader(LOADER_ITEM_TAGS, null, this);
            getLoaderManager().initLoader(LOADER_EXCLUDE_ITEMS, null, this);
        } else {
            isNewItem = true;
        }
        //Start Loader for all the necessary Data for the GUI.
        getLoaderManager().initLoader(LOADER_PROJECT, null, this);
        getLoaderManager().initLoader(LOADER_PARTICIPANTS, null, this);
        getLoaderManager().initLoader(LOADER_TAGS, null, this);
        getLoaderManager().initLoader(LOADER_CURRENCIES, null, this);


        //Initialize View-Variables
        tagsAddedLayout = (LinearLayout) findViewById(R.id.linearLayoutTags);
        payersList = (ListView) findViewById(R.id.listViewPayers);
        excludeList = (ListView) findViewById(R.id.listViewExcludeItems);
        itemNameEditText = (EditText) findViewById(R.id.editTextItemName);

        //Initialize global Variables
        tagViewPool = new Stack<View>();
        tagTextPool = new Stack<TextView>();


        //Get default Currency data
        defaultCurrencyId = Long.parseLong(preferences.getString(getString(R.string.pref_default_currency), "-1"));
        Uri defaultCurrencyUri = ContentUris.withAppendedId(budgetSplitContract.currencies.CONTENT_URI, defaultCurrencyId);
        String projection[] = {budgetSplitContract.currencies.COLUMN_CURRENCY_CODE, budgetSplitContract.currencies.COLUMN_EXCHANGE_RATE};
        Cursor defaultCurrencyCursor = getContentResolver().query(defaultCurrencyUri, projection, null, null, null);
        if (defaultCurrencyCursor.getCount() > 0) {
            defaultCurrencyCursor.moveToFirst();
            defaultCurrencyCode = defaultCurrencyCursor.getString(defaultCurrencyCursor.getColumnIndex(budgetSplitContract.currencies.COLUMN_CURRENCY_CODE));
            defaultCurrencyExchangeRate = defaultCurrencyCursor.getDouble(defaultCurrencyCursor.getColumnIndex(budgetSplitContract.currencies.COLUMN_EXCHANGE_RATE));
        } else {
            throw new IllegalArgumentException("The currency saved in SharedPreferences was not found in db.");
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.add_new_item, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    //Loader Methods
    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        switch (i) {
            case LOADER_ITEM:
                return new CursorLoader(getApplicationContext(), itemUri, budgetSplitContract.itemsDetailsRO.PROJECTION_ALL, null, null, null);
            case LOADER_ITEM_TAGS:
                return new CursorLoader(getApplicationContext(), ContentUris.withAppendedId(budgetSplitContract.itemsTagsDetailsRO.CONTENT_URI_SINGLE_ITEM, itemId), budgetSplitContract.itemsTagsDetailsRO.PROJECTION_ALL, null, null, null);
            case LOADER_ITEM_PARTICIPANTS:
                return new CursorLoader(getApplicationContext(), ContentUris.withAppendedId(budgetSplitContract.itemsParticipantsDetailsRO.CONTENT_URI_SINGLE_ITEM, itemId), budgetSplitContract.itemsParticipantsDetailsRO.PROJECTION_ALL, null, null, null);
            case LOADER_TAGS:
                return new CursorLoader(getApplicationContext(), budgetSplitContract.tags.CONTENT_URI, budgetSplitContract.tags.PROJECTION_ALL, null, null, null);
            case LOADER_CURRENCIES:
                return new CursorLoader(getApplicationContext(), budgetSplitContract.currencies.CONTENT_URI, budgetSplitContract.currencies.PROJECTION_ALL, null, null, null);
            case LOADER_PARTICIPANTS:
                //TODO Change CursorLoader so that only ProjectParticipants will be showed.
                return new CursorLoader(getApplicationContext(), budgetSplitContract.participants.CONTENT_URI, budgetSplitContract.participants.PROJECTION_ALL, null, null, null);

            case LOADER_PROJECT:
                String[] projection = {budgetSplitContract.projectsDetailsRO._ID,
                        budgetSplitContract.projectsDetailsRO.COLUMN_PROJECT_NAME,
                        budgetSplitContract.projectsDetailsRO.COLUMN_PROJECT_ADMIN_ID,
                        budgetSplitContract.projectsDetailsRO.COLUMN_PROJECT_ADMIN_NAME,
                        budgetSplitContract.projectsDetailsRO.COLUMN_PROJECT_ADMIN_UNIQUEID};
                return new CursorLoader(getApplicationContext(), budgetSplitContract.projectsDetailsRO.CONTENT_URI, projection, null, null, null);

            case LOADER_EXCLUDE_ITEMS:
                return new CursorLoader(getApplicationContext(), budgetSplitContract.excludeItems.CONTENT_URI, budgetSplitContract.excludeItems.PROJECTION_ALL, null, null, null);
            default:
                throw new IllegalArgumentException("The LoaderId i didn't match with any of the defined Loaders.");
        }
    }

    @Override
    synchronized public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        switch (cursorLoader.getId()) {
            case LOADER_ITEM:
                //call drawGUI only if Load finished for the first time.
                cursorItem = cursor;
                if (loaderExcludeItemsFinishedInitialLoading && loaderParticipantsFinishedInitialLoading && loaderCurrenciesFinishedInitialLoading && loaderItemParticipantsFinishedInitialLoading
                        && loaderItemTagsFinishedInitialLoading && loaderProjectFinishedInitialLoading && loaderTagsFinishedInitialLoading && !loaderItemFinishedInitialLoading) {
                    drawGUIForExistingItem();
                }
                loaderItemFinishedInitialLoading = true;
                break;


            //If activity was started with a new item, drawGUIForNewItem() is called, if all necessary loaders finished their work. If activity was started with an existing
            //item, drawGUIForExistingItem() is called if all all necessary loaders finished their work. This Loader is destroyed after initial Load. Future changes in the Content Providers
            // project Table don't take effect.
            case LOADER_PROJECT:
                cursorProject = cursor;
                if (isNewItem) {
                    if (loaderParticipantsFinishedInitialLoading && loaderCurrenciesFinishedInitialLoading && loaderTagsFinishedInitialLoading && !loaderProjectFinishedInitialLoading) {
                        drawGUIForNewItem();
                    }
                }
                if (loaderExcludeItemsFinishedInitialLoading && loaderItemFinishedInitialLoading && loaderParticipantsFinishedInitialLoading && loaderCurrenciesFinishedInitialLoading && loaderItemParticipantsFinishedInitialLoading
                        && loaderItemTagsFinishedInitialLoading && loaderTagsFinishedInitialLoading && !loaderProjectFinishedInitialLoading) {
                    drawGUIForExistingItem();
                }
                loaderProjectFinishedInitialLoading = true;
                break;


            //If activity was started with a new item, drawGUIForNewItem() is called, if all necessary loaders finished their work. If activity was started with an existing
            //item, drawGUIForExistingItem() of redrawGUIForExistingItem() is called if all all necessary loaders finished their work.
            case LOADER_TAGS:
                //New Item
                if (isNewItem) {
                    if (loaderParticipantsFinishedInitialLoading && loaderCurrenciesFinishedInitialLoading && loaderProjectFinishedInitialLoading) {
                        if (loaderTagsFinishedInitialLoading) {
                            refreshTags(cursor);
                            cursorTags = cursor;
                        } else {
                            cursorTags = cursor;
                            drawGUIForNewItem();
                        }
                    } else {
                        cursorTags = cursor;
                    }
                }
                //Existing Item
                else if (loaderExcludeItemsFinishedInitialLoading && loaderParticipantsFinishedInitialLoading && loaderItemFinishedInitialLoading && loaderCurrenciesFinishedInitialLoading && loaderItemParticipantsFinishedInitialLoading
                        && loaderItemTagsFinishedInitialLoading && loaderProjectFinishedInitialLoading) {
                    if (loaderTagsFinishedInitialLoading) {
                        refreshTags(cursor);
                        cursorTags = cursor;
                    } else {
                        cursorTags = cursor;
                        drawGUIForExistingItem();
                    }
                } else {
                    cursorTags = cursor;
                }
                loaderTagsFinishedInitialLoading = true;
                break;


            //If activity was started with a new item, drawGUIForNewItem() is called, if all necessary loaders finished their work. If activity was started with an existing
            //item, drawGUIForExistingItem() of redrawGUIForExistingItem() is called if all all necessary loaders finished their work.
            case LOADER_CURRENCIES:
                //New Item
                if (isNewItem) {
                    if (loaderParticipantsFinishedInitialLoading && loaderTagsFinishedInitialLoading && loaderProjectFinishedInitialLoading) {
                        if (loaderCurrenciesFinishedInitialLoading) {
                            refreshCurrencies(cursor);
                            cursorCurrencies = cursor;
                        } else {
                            cursorCurrencies = cursor;
                            drawGUIForNewItem();
                        }
                    } else {
                        cursorCurrencies = cursor;
                    }
                }
                //Existing Item
                else if (loaderExcludeItemsFinishedInitialLoading && loaderParticipantsFinishedInitialLoading && loaderItemFinishedInitialLoading && loaderTagsFinishedInitialLoading && loaderItemParticipantsFinishedInitialLoading
                        && loaderItemTagsFinishedInitialLoading && loaderProjectFinishedInitialLoading) {
                    if (loaderCurrenciesFinishedInitialLoading) {
                        refreshCurrencies(cursor);
                        cursorTags = cursor;
                    } else {
                        cursorCurrencies = cursor;
                        drawGUIForExistingItem();
                    }
                } else {
                    cursorCurrencies = cursor;
                }
                loaderCurrenciesFinishedInitialLoading = true;
                break;


            //If activity was started with a new item, drawGUIForNewItem() is called, if all necessary loaders finished their work. If activity was started with an existing
            //item, drawGUIForExistingItem() of redrawGUIForExistingItem() is called if all all necessary loaders finished their work.
            case LOADER_PARTICIPANTS:
                //New Item
                if (isNewItem) {
                    if (loaderCurrenciesFinishedInitialLoading && loaderTagsFinishedInitialLoading && loaderProjectFinishedInitialLoading) {
                        if (loaderParticipantsFinishedInitialLoading) {
                            refreshParticipants(cursor);
                            cursorParticipants = cursor;
                        } else {
                            cursorParticipants = cursor;
                            drawGUIForNewItem();
                        }
                    } else {
                        cursorParticipants = cursor;
                    }
                }
                //Existing Item
                else if (loaderExcludeItemsFinishedInitialLoading && loaderCurrenciesFinishedInitialLoading && loaderItemFinishedInitialLoading && loaderTagsFinishedInitialLoading && loaderItemParticipantsFinishedInitialLoading
                        && loaderItemTagsFinishedInitialLoading && loaderProjectFinishedInitialLoading) {
                    if (loaderParticipantsFinishedInitialLoading) {
                        refreshParticipants(cursor);
                        cursorParticipants = cursor;
                    } else {
                        cursorParticipants = cursor;
                        drawGUIForExistingItem();
                    }
                } else {
                    cursorParticipants = cursor;
                }
                loaderParticipantsFinishedInitialLoading = true;
                break;


            //This case is only reached, if activity was started for an existing item. Determines, whether to wait for other Loaders or to call the drawGUIForExistingItem() or refresh-Method.
            //This Loader is destroyed after initial Load. Future changes in the Content Providers
            // project Table don't take effect.
            case LOADER_ITEM_PARTICIPANTS:
                cursorItemParticipants = cursor;
                if (loaderExcludeItemsFinishedInitialLoading && loaderParticipantsFinishedInitialLoading && loaderItemFinishedInitialLoading && loaderCurrenciesFinishedInitialLoading && loaderTagsFinishedInitialLoading
                        && loaderItemTagsFinishedInitialLoading && loaderProjectFinishedInitialLoading && !loaderItemParticipantsFinishedInitialLoading) {
                    drawGUIForExistingItem();
                }
                loaderItemParticipantsFinishedInitialLoading = true;
                break;


            //This case is only reached, if activity was started for an existing item. Determines, whether to wait for other Loaders or to call the drawGUIForExistingItem() or refresh-Method.
            case LOADER_ITEM_TAGS:
                cursorItemTags = cursor;
                if (loaderExcludeItemsFinishedInitialLoading && loaderParticipantsFinishedInitialLoading && loaderItemFinishedInitialLoading && loaderCurrenciesFinishedInitialLoading && loaderItemParticipantsFinishedInitialLoading
                        && loaderTagsFinishedInitialLoading && loaderProjectFinishedInitialLoading && !loaderItemTagsFinishedInitialLoading) {
                    drawGUIForExistingItem();
                }
                loaderItemTagsFinishedInitialLoading = true;
                break;

            case LOADER_EXCLUDE_ITEMS:
                cursorExcludeItems = cursor;
                if (loaderItemTagsFinishedInitialLoading && loaderParticipantsFinishedInitialLoading && loaderItemFinishedInitialLoading && loaderCurrenciesFinishedInitialLoading && loaderItemParticipantsFinishedInitialLoading
                        && loaderTagsFinishedInitialLoading && loaderProjectFinishedInitialLoading && !loaderExcludeItemsFinishedInitialLoading) {
                    drawGUIForExistingItem();
                }
                loaderExcludeItemsFinishedInitialLoading = true;
                break;
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        switch (cursorLoader.getId()) {
            case LOADER_TAGS:
                if (tagChooserPopup != null) {
                    tagChooserPopup.dismiss();
                    tagChooserPopup = null;
                }
                addNewTagButton.setEnabled(false);
                break;
            case LOADER_CURRENCIES:
                payerAdapter.closeCursorForAllCurrencyAdapter();
                break;
            case LOADER_PARTICIPANTS:
                if (payerChooserPopup != null) {
                    payerChooserPopup.dismiss();
                    payerChooserPopup = null;
                }
                addNewPayerButton.setEnabled(false);
                break;
        }
    }


    //Methods to refresh GUI
    void refreshTags(Cursor newTagsCursor) {
        CursorJoiner cursorJoiner = new CursorJoiner(cursorTags, new String[]{budgetSplitContract.tags._ID}, newTagsCursor, new String[]{budgetSplitContract.tags._ID});
        for (CursorJoiner.Result joinerResult : cursorJoiner) {
            switch (joinerResult) {
                case RIGHT:
                    Tag newTag = new Tag(newTagsCursor.getLong(newTagsCursor.getColumnIndex(budgetSplitContract.tags._ID)), newTagsCursor.getString(newTagsCursor.getColumnIndex(budgetSplitContract.tags.COLUMN_NAME)));
                    tagsNotAdded.add(newTag);
                    break;
                case LEFT:
                    for (Tag tag : tagsToAdd) {
                        if (tag.id == cursorTags.getLong(cursorTags.getColumnIndex(budgetSplitContract.tags._ID))) {
                            tagsToAdd.remove(tag);
                            break;
                        }
                    }
                    break;
                case BOTH:
                    //Do nothing.
                    break;
            }
        }

        if (tagChooserPopup != null) {
            tagChooserPopup.dismiss();
            tagChooserPopup = null;
        }
        addNewTagButton.setEnabled(true);
    }

    void refreshCurrencies(Cursor newCurrenciesCursor) {
        payerAdapter.swapCursorForAllCurrencyAdapter(newCurrenciesCursor);
    }

    void refreshParticipants(Cursor newParticipantsCursor) {
        if (payerChooserPopup != null) {
            payerChooserPopup.dismiss();
            payerChooserPopup = null;
        }
        addNewPayerButton.setEnabled(true);
    }


    //Methods to draw Gui on start.
    void drawGUIForExistingItem() {
        //Write privilege-flags
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        myUniqueId = preferences.getString(getString(R.string.pref_user_unique_id), "noIdFound");
        if (cursorItem.getCount() == 0) {
            throw new IllegalArgumentException("The Item added to Intent does not exist in database.");
        }
        cursorItem.moveToFirst();
        fullAccess = cursorItem.getInt(cursorItem.getColumnIndex(budgetSplitContract.itemsDetailsRO.COLUMN_CREATOR_IS_VIRTUAL)) > 0; //Owner is virtual.
        String ownersUniqueId = cursorItem.getString(cursorItem.getColumnIndex(budgetSplitContract.itemsDetailsRO.COLUMN_CREATOR_UNIQUE_ID));
        fullAccess = fullAccess || cursorProject.getString(cursorProject.getColumnIndex(budgetSplitContract.projectsDetailsRO.COLUMN_PROJECT_ADMIN_UNIQUEID)).equals(myUniqueId); //I am admin
        fullAccess = fullAccess || ownersUniqueId.equals(myUniqueId);//I am Item-owner

        //Add Listener to itemname EditText
        itemNameEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                switch (i) {
                    case EditorInfo.IME_ACTION_DONE:
                        if (!textView.getText().toString().trim().equals("")) {
                            textView.clearFocus();
                            InputMethodManager mnr = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                            mnr.hideSoftInputFromWindow(textView.getWindowToken(), 0);
                            return true;
                        } else {
                            Toast.makeText(getBaseContext(), getString(R.string.please_enter_a_valid_item_name), Toast.LENGTH_SHORT).show();
                            textView.requestFocus();
                            return true;
                        }
                    default:
                        return false;
                }
            }
        });

        itemNameEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean b) {
                if (!b) {
                    if (!((TextView) view).getText().toString().trim().equals("")) {
                        view.clearFocus();
                        InputMethodManager mnr = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                        mnr.hideSoftInputFromWindow(view.getWindowToken(), 0);
                    } else {
                        Toast.makeText(getBaseContext(), getString(R.string.please_enter_a_valid_item_name), Toast.LENGTH_SHORT).show();
                        view.requestFocus();
                    }
                }
            }
        });

        //Draw Tag GUI elements
        tagsAlreadyAdded = new ArrayList<Tag>();
        tagsNotAdded = new ArrayList<Tag>();
        CursorJoiner joiner = new CursorJoiner(cursorTags, new String[]{budgetSplitContract.tags._ID}, cursorItemTags, new String[]{budgetSplitContract.itemsTagsDetailsRO.COLUMN_TAG_ID});
        //Add Tags to respective Array List.
        for (CursorJoiner.Result result : joiner) {
            switch (result) {
                case LEFT:
                    Tag tagLeft = new Tag(cursorTags.getLong(cursorTags.getColumnIndex(budgetSplitContract.tags._ID)),
                            cursorTags.getString(cursorTags.getColumnIndex(budgetSplitContract.tags.COLUMN_NAME)));
                    tagsNotAdded.add(tagLeft);
                    break;
                case BOTH:
                    Tag tagRight = new Tag(cursorItemTags.getLong(cursorItemTags.getColumnIndex(budgetSplitContract.itemsTagsDetailsRO.COLUMN_TAG_ID)),
                            cursorItemTags.getString(cursorItemTags.getColumnIndex(budgetSplitContract.itemsTagsDetailsRO.COLUMN_TAG_NAME)));
                    tagsAlreadyAdded.add(tagRight);
                    break;
            }
        }
        tagsToAdd = new ArrayList<Tag>();
        tagsToDelete = new ArrayList<Tag>();
        tagsAddedViews = TagHelper.TagsToCustomView(tagsAddedLayout, tagsAlreadyAdded);
        for (View view : tagsAddedViews) {
            tagsAddedLayout.addView(view, tagsAddedLayout.getChildCount() - 1);
        }
        addNewTagButton = new ImageButton(getApplicationContext());
        addNewTagButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_new));
        addNewTagButton.setLayoutParams(new ViewGroup.LayoutParams(100, 100));
        addNewTagButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showTagsPopupWindow(view, true);
            }
        });
        //Disable all tags so they can't be edited.
        if (!fullAccess) {
            for (View view : tagsAddedViews) {
                view.setEnabled(false);
            }
        } else {
            tagsAddedLayout.addView(addNewTagButton, tagsAddedLayout.getChildCount()); //If there is edit access, add the "add tag" button.
        }


        //Draw Payer GUI elements
        payersToAdd = new ArrayList<Payer>();
        payersToUpdate = new ArrayList<Payer>();
        payersToDelete = new ArrayList<Payer>();
        payersAlreadyAdded = payerCursorToList(cursorItemParticipants);
        ArrayList<Payer> payersInList = new ArrayList<Payer>();
        payersInList.addAll(payersAlreadyAdded);
        payerAdapter = new ItemParticipantsAdapter(getBaseContext(), R.layout.activity_add_item_participants_row, payersInList);
        payersList.setAdapter(payerAdapter);
        View payersListFooter = getLayoutInflater().inflate(R.layout.activity_add_item_participants_footer, null);
        addNewPayerButton = (ImageButton) payersListFooter.findViewById(R.id.imageButton);
        addNewPayerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (fullAccess) {
                    showPayerChooserPopup(view);
                }
            }
        });
        payersList.addFooterView(payersListFooter);

        //Draw ExcludeItem GUI elements
        excludeItemsToAdd = new ArrayList<ExcludeItem>();
        excludeItemsToUpdate = new ArrayList<ExcludeItem>();
        excludeItemsToDelete = new ArrayList<ExcludeItem>();
        excludeItemsAlreadyAdded = excludeItemsCursorToList(cursorExcludeItems);
        ArrayList<ExcludeItem> excludeItemsInList = new ArrayList<ExcludeItem>();
        excludeItemsInList.addAll(excludeItemsAlreadyAdded);
        excludeItemsAdapter = new ExcludeItemsAdapter(getBaseContext(), R.layout.activity_add_new_item_exclude_participant_row, excludeItemsInList);
        excludeList.setAdapter(excludeItemsAdapter);
        View excludeItemFooter = getLayoutInflater().inflate(R.layout.activity_add_new_item_exclude_participant_footer, null);
        excludeSomeoneButton = (ImageButton) excludeItemFooter.findViewById(R.id.imageButton);
        excludeSomeoneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showPayerChooserPopup(view);
            }
        });
        excludeList.addFooterView(excludeItemFooter);


        //Draw item name View
        itemNameEditText.setText(cursorItem.getString(cursorItem.getColumnIndex(budgetSplitContract.itemsDetailsRO.COLUMN_ITEM_NAME)));
        itemNameEditText.setEnabled(fullAccess); //Disable EditText if there is no full Access on Item.

        //Change the Label of the "Add Item" Button and add an OnClickListener
        Button addItemButton = (Button) findViewById(R.id.buttonAddItem);
        addItemButton.setText(getString(R.string.save_item));
        addItemButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new BackgroundSaver().execute();
            }
        });
    }

    void drawGUIForNewItem() {
        //Write privilege-flags
        fullAccess = true;

        //Add listener to itemname editText
        itemNameEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                switch (i) {
                    case EditorInfo.IME_ACTION_DONE:
                        if (!textView.getText().toString().trim().equals("")) {
                            textView.clearFocus();
                            InputMethodManager mnr = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                            mnr.hideSoftInputFromWindow(textView.getWindowToken(), 0);
                            return true;
                        } else {
                            Toast.makeText(getBaseContext(), getString(R.string.please_enter_a_valid_item_name), Toast.LENGTH_SHORT).show();
                            textView.requestFocus();
                            return true;
                        }
                    default:
                        return false;
                }
            }
        });

        itemNameEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean b) {
                if (!b) {
                    if (!((TextView) view).getText().toString().trim().equals("")) {
                        view.clearFocus();
                        InputMethodManager mnr = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                        mnr.hideSoftInputFromWindow(view.getWindowToken(), 0);
                    } else {
                        Toast.makeText(getBaseContext(), getString(R.string.please_enter_a_valid_item_name), Toast.LENGTH_SHORT).show();
                        view.requestFocus();
                    }
                }
            }
        });

        //Draw Tag GUI elements
        tagsAddedViews = new ArrayList<View>();
        tagsAlreadyAdded = new ArrayList<Tag>();
        tagsNotAdded = tagCursorToList(cursorTags);
        tagsToAdd = new ArrayList<Tag>();
        tagsToDelete = new ArrayList<Tag>();
        addNewTagButton = new ImageButton(getApplicationContext());
        addNewTagButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_new));
        addNewTagButton.setLayoutParams(new ViewGroup.LayoutParams(100, 100));
        addNewTagButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showTagsPopupWindow(view, true);
            }
        });
        tagsAddedLayout.addView(addNewTagButton, tagsAddedLayout.getChildCount());

        //Draw Payer GUI elements
        payersToAdd = new ArrayList<Payer>();
        payersToUpdate = new ArrayList<Payer>();
        payersToDelete = new ArrayList<Payer>();
        payersAlreadyAdded = new ArrayList<Payer>();
        payerAdapter = new ItemParticipantsAdapter(getBaseContext(), R.layout.activity_add_item_participants_row, new ArrayList<Payer>());
        View payersListFooter = getLayoutInflater().inflate(R.layout.activity_add_item_participants_footer, null);
        payersList.addFooterView(payersListFooter);
        payersList.setAdapter(payerAdapter);
        addNewPayerButton = (ImageButton) payersListFooter.findViewById(R.id.imageButton);
        addNewPayerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showPayerChooserPopup(view);
            }
        });

        //Draw ExcludeItem GUI elements
        excludeItemsToAdd = new ArrayList<ExcludeItem>();
        excludeItemsToUpdate = new ArrayList<ExcludeItem>();
        excludeItemsToDelete = new ArrayList<ExcludeItem>();
        excludeItemsAlreadyAdded = new ArrayList<ExcludeItem>();
        excludeItemsAdapter = new ExcludeItemsAdapter(getBaseContext(), R.layout.activity_add_new_item_exclude_participant_row, new ArrayList<ExcludeItem>());
        View excludeItemFooter = getLayoutInflater().inflate(R.layout.activity_add_item_participants_footer, null);
        excludeList.addFooterView(excludeItemFooter);
        excludeList.setAdapter(excludeItemsAdapter);
        excludeSomeoneButton = (ImageButton) excludeItemFooter.findViewById(R.id.imageButton);
        excludeSomeoneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showExcludeItemPopup();
            }
        });


        //Add Listener to AddItem-Button
        Button addItemButton = (Button) findViewById(R.id.buttonAddItem);
        addItemButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new BackgroundSaver().execute();
            }
        });
    }


    /**
     * Used to save Items. The Task will close the Activity, if saving was possible. Otherwise it will show a Error-Message to the user.
     */
    private class BackgroundSaver extends AsyncTask<Void, Void, String> {

        @Override
        protected String doInBackground(Void... voids) {
            ArrayList<ContentProviderOperation> operations = new ArrayList<ContentProviderOperation>();
            ContentValues newItemValues = new ContentValues();

            //Check if Item Name isn't empty.
            if (itemNameEditText.getText().toString().trim().length() > 0) {
                newItemValues.put(budgetSplitContract.items.COLUMN_NAME, itemNameEditText.getText().toString().replaceAll("\n", " "));
            } else {
                return getString(R.string.illegal_item_name);
            }
            if (isNewItem) {
                newItemValues.put(budgetSplitContract.items.COLUMN_PROJECT, projectId);
                newItemValues.put(budgetSplitContract.items.COLUMN_CREATOR, myParticipantsId);
                itemId = ContentUris.parseId(getContentResolver().insert(budgetSplitContract.items.CONTENT_URI, newItemValues));
                if (itemId == -1) {
                    return getString(R.string.couldnt_add_item_to_db);
                }
            } else {
                Uri item = ContentUris.withAppendedId(budgetSplitContract.items.CONTENT_URI, itemId);
                int itemsUpdated = getContentResolver().update(item, newItemValues, null, null);
                if (itemsUpdated != 1) {
                    return getString(R.string.couldnt_update_item_in_db);
                }
            }


            //Check if all payers have a valid Currency and Value.
            for (Payer payer : payersToAdd) {
                ContentValues newPayer = new ContentValues();
                if (payer.currencyId != -1) {
                    newPayer.put(budgetSplitContract.itemsParticipants.COLUMN_CURRENCY_ID, payer.currencyId);
                } else {
                    deleteItem(isNewItem, itemId);
                    return getString(R.string.please_select_a_currency_for_) + payer.payerName;
                }
                if (payer.amountPayed != 0) {
                    newPayer.put(budgetSplitContract.itemsParticipants.COLUMN_AMOUNT_PAYED, payer.amountPayed);
                } else {
                    deleteItem(isNewItem, itemId);
                    return getString(R.string.please_enter_a_amount_bigger_than_zero_for_) + payer.payerName;
                }
                newPayer.put(budgetSplitContract.itemsParticipants.COLUMN_ITEM_ID, itemId);
                newPayer.put(budgetSplitContract.itemsParticipants.COLUMN_PARTICIPANTS_ID, payer.payerId);
                operations.add(ContentProviderOperation.newInsert(budgetSplitContract.itemsParticipants.CONTENT_URI)
                        .withValues(newPayer)
                        .build());
            }
            for (Payer payer : payersToUpdate) {
                ContentValues updatedPayer = new ContentValues();
                if (payer.currencyId != -1) {
                    updatedPayer.put(budgetSplitContract.itemsParticipants.COLUMN_CURRENCY_ID, payer.currencyId);
                } else {
                    return getString(R.string.please_select_a_currency_for_) + payer.payerName;
                }
                if (payer.amountPayed != 0) {
                    updatedPayer.put(budgetSplitContract.itemsParticipants.COLUMN_AMOUNT_PAYED, payer.amountPayed);
                } else {
                    return getString(R.string.please_enter_a_amount_bigger_than_zero_for_) + payer.payerName;
                }
                Uri itemParticipantUri = ContentUris.withAppendedId(budgetSplitContract.itemsParticipants.CONTENT_URI, payer.rowId);
                operations.add(ContentProviderOperation.newUpdate(itemParticipantUri)
                        .withValues(updatedPayer).build());
            }
            for (Payer payer : payersToDelete) {
                Uri payerToDeleteUri = ContentUris.withAppendedId(budgetSplitContract.itemsParticipants.CONTENT_URI, payer.rowId);
                operations.add(ContentProviderOperation.newDelete(payerToDeleteUri).build());
            }

            for (Tag tag : tagsToAdd) {
                ContentValues newTagValues = new ContentValues();
                newTagValues.put(budgetSplitContract.itemsTags.COLUMN_ITEM_ID, itemId);
                newTagValues.put(budgetSplitContract.itemsTags.COLUMN_TAGS_ID, tag.id);
                operations.add(ContentProviderOperation.newInsert(budgetSplitContract.itemsTags.CONTENT_URI).withValues(newTagValues).build());
            }
            for (Tag tag : tagsToDelete) {
                operations.add(ContentProviderOperation.newDelete(budgetSplitContract.itemsTags.CONTENT_URI).withSelection(budgetSplitContract.itemsTags.COLUMN_TAGS_ID + " = " + tag.id, null).build());
            }

            for (ExcludeItem excludeItem : excludeItemsToAdd) {
                ContentValues newExcludeItem = new ContentValues();
                if (excludeItem.shareRatio <= 1 && excludeItem.shareRatio >= 0) {
                    newExcludeItem.put(budgetSplitContract.excludeItems.COLUMN_SHARE_RATIO, excludeItem.shareRatio);
                } else {
                    deleteItem(isNewItem, itemId);
                    return getString(R.string.please_enter_a_value_between_100_and_0) + getString(R.string._for_) + excludeItem.participantName;
                }
                newExcludeItem.put(budgetSplitContract.excludeItems.COLUMN_ITEM_ID, itemId);
                newExcludeItem.put(budgetSplitContract.excludeItems.COLUMN_PARTICIPANTS_ID, excludeItem.participantId);
                operations.add(ContentProviderOperation.newInsert(budgetSplitContract.excludeItems.CONTENT_URI).withValues(newExcludeItem).build());
            }

            for (ExcludeItem excludeItem : excludeItemsToUpdate) {
                ContentValues updatedExcludeItem = new ContentValues();
                if (excludeItem.shareRatio <= 1 && excludeItem.shareRatio >= 0) {
                    updatedExcludeItem.put(budgetSplitContract.excludeItems.COLUMN_SHARE_RATIO, excludeItem.shareRatio);
                } else {
                    deleteItem(isNewItem, itemId);
                    return getString(R.string.please_enter_a_value_between_100_and_0) + getString(R.string._for_) + excludeItem.participantName;
                }
                Uri uriToUpdate = ContentUris.withAppendedId(budgetSplitContract.excludeItems.CONTENT_URI, excludeItem.rowid);
                operations.add((ContentProviderOperation.newUpdate(uriToUpdate).withValues(updatedExcludeItem).build()));
            }

            for (ExcludeItem excludeItem : excludeItemsToDelete) {
                Uri toDelete = ContentUris.withAppendedId(budgetSplitContract.excludeItems.CONTENT_URI, excludeItem.rowid);
                operations.add(ContentProviderOperation.newDelete(toDelete).build());
            }

            try {
                getContentResolver().applyBatch(budgetSplitContract.AUTHORITY, operations);
            } catch (RemoteException e) {
                e.printStackTrace();
            } catch (OperationApplicationException e) {
                e.printStackTrace();
            }

            return null;
        }

        void deleteItem(boolean isNewItem, long itemId) {
            if (isNewItem) {
                getContentResolver().delete(ContentUris.withAppendedId(budgetSplitContract.items.CONTENT_URI, itemId), null, null);
            }
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            if (s != null) {
                Intent data = new Intent();
                data.putExtra(ProjectOverview.RESULT_EXTRA_ERROR_MESSAGE, s);
                Toast.makeText(getBaseContext(), s, Toast.LENGTH_LONG).show();
                setResult(ProjectOverview.RESULT_CODE_ERROR);
            } else {
                setResult(RESULT_OK);
                finish();
            }
        }
    }


    //Methods to handle Tags
    private void addTag(View view) {
        Tag tag = (Tag) view.getTag();
        tagsNotAdded.remove(tag); //Remove Tag from List.
        //check if tag was already in db.
        if (tagsAlreadyAdded.contains(tag)) {
            tagsToDelete.remove(tag);
        } else {
            tagsToAdd.add(tag);
        }
        //Remove view from tag chooser and view List.
        tagsNotAddedViews.remove(view);
        tagChooserList.removeView(view);
        tagTextPool.push((TextView) view); //Add TextView to Pool for recycling.
        View newTagView = tag.getTagView(tagsAddedLayout);  //Create or recycle new TagView to add to Activity.
        tagsAddedViews.add(newTagView);
        tagsAddedLayout.addView(newTagView, tagsAddedLayout.getChildCount() - 1); //Add the new tag just behind the "add tag" Button.
    }

    private void deleteTag(View view) {
        Tag tag = (Tag) view.getTag();
        //Check if tag is already in db.
        if (tagsAlreadyAdded.contains(tag)) {
            tagsToDelete.add(tag);
        } else {
            tagsToAdd.remove(tag);
        }
        tagsNotAdded.add(tag);  //Add tag to List.
        //Remove view from LinearLayout.
        View tagView = (View) view.getParent();
        tagsAddedViews.remove(tagView);
        tagsAddedLayout.removeView(tagView);
        tagViewPool.push(tagView); //Add View to Pool for recycling.
        if (tagChooserList == null) {
            showTagsPopupWindow(null, false); //Preloads tagsChooser so that tagChooserList does exist.
        }
        TextView newTagView = tag.getTagTextView(tagChooserList); //Create or recycle new TextView to add to TagChooser.
        tagsNotAddedViews.add(newTagView);
        tagChooserList.addView(newTagView, 0); //Add the Tag at first position to Tag-Chooser-List.
    }

    private void showTagsPopupWindow(View view, boolean show) {
        if (tagChooserPopup == null) {
            View tagChooserLayout = ((LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE)).inflate(R.layout.activity_add_item_tag_chooser, null);
            tagChooserList = (LinearLayout) tagChooserLayout.findViewById(R.id.scrollView).findViewById(R.id.LinearLayoutTags);
            tagsNotAddedViews = TagHelper.TagsToTextView(tagChooserList, tagsNotAdded);
            for (View tagView : tagsNotAddedViews) {
                tagChooserList.addView(tagView, 0);
            }
            ImageButton createNewTag = new ImageButton(getBaseContext());
            createNewTag.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_new));
            createNewTag.setLayoutParams(new LinearLayout.LayoutParams(60, 60));
            createNewTag.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    tagChooserPopup.dismiss();
                    showCreateTagPopup(view);
                }
            });
            LinearLayout linearLayoutTags = (LinearLayout) tagChooserLayout.findViewById(R.id.scrollView).findViewById(R.id.LinearLayoutTags);
            linearLayoutTags.addView(createNewTag);
            tagChooserLayout.findViewById(R.id.buttonCancel).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    tagChooserPopup.dismiss();
                }
            });
            tagChooserPopup = new PopupWindow(tagChooserLayout, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        }
        if (show)
            if (show) tagChooserPopup.showAtLocation(view, Gravity.CENTER, 0, 0);
    }

    void showCreateTagPopup(final View view) {
        if (tagCreatePopup == null) {
            AlertDialog.Builder myDialogBuilder = new AlertDialog.Builder(this);
            myDialogBuilder.setTitle(getString(R.string.create_a_new_tag));
            final EditText editText = new EditText(getBaseContext());
            editText.setHint(getString(R.string.tag_name));
            myDialogBuilder.setView(editText);
            myDialogBuilder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.cancel();
                }
            });
            myDialogBuilder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    ContentValues newTag = new ContentValues();
                    newTag.put(budgetSplitContract.tags.COLUMN_NAME, editText.getText().toString());
                    getContentResolver().insert(budgetSplitContract.tags.CONTENT_URI, newTag);
                    dialogInterface.dismiss();
                    showTagsPopupWindow(view, true);
                }
            });
            tagCreatePopup = myDialogBuilder.create();
        }
        tagCreatePopup.show();

    }

    /**
     * Class Data Type for use in ArrayAdapters.
     */
    public class Tag {
        long id;
        String name;

        public Tag(long id, String name) {
            this.id = id;
            this.name = name;
        }

        /**
         * @param root The ParenView of the Views to create.
         * @return Returns a CustomView Tag filled with the name of the tag and a link to this object added as tag of the delete Button.
         */
        View getTagView(ViewGroup root) {
            View newView;
            if (tagViewPool.empty()) {
                newView = ((LayoutInflater) getBaseContext().getSystemService(LAYOUT_INFLATER_SERVICE)).inflate(R.layout.activity_add_item_tag, null);
            } else {
                newView = tagViewPool.pop();
            }
            ((TextView) newView.findViewById(R.id.textViewTagName)).setText(name);
            ImageButton tagDeleteButton = (ImageButton) newView.findViewById(R.id.imageButtonDelete);
            tagDeleteButton.setTag(this);
            tagDeleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    deleteTag(view);
                }
            });
            return newView;
        }

        /**
         * @param root The ParenView of the Views to create.
         * @return Returns a TextView filled with the name of the tag and a link to this object as tag of the TextView.
         */
        TextView getTagTextView(ViewGroup root) {
            TextView newView;
            if (tagTextPool.empty()) {
                newView = new TextView(root.getContext());
                newView.setTextAppearance(getBaseContext(), android.R.style.TextAppearance_Holo_Medium_Inverse);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.setMargins(10, 10, 10, 10);
                newView.setLayoutParams(params);
            } else {
                newView = tagTextPool.pop();
            }
            newView.setText(name);
            newView.setTag(this);
            newView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    addTag(view);
                }
            });
            return newView;
        }


    }

    /**
     * Converts a Cursor of tags to a {@link java.util.List} of Tags.
     * The Cursor must at least Contain the Columns {@value ch.ethz.itet.pps.budgetSplit.contentProvider.budgetSplitContract.tags#_ID}  and {@value ch.ethz.itet.pps.budgetSplit.contentProvider.budgetSplitContract.tags#COLUMN_NAME}. If the more specific Column identifiers
     * {@value ch.ethz.itet.pps.budgetSplit.contentProvider.budgetSplitContract.itemsTagsDetailsRO#COLUMN_TAG_ID} and {@value ch.ethz.itet.pps.budgetSplit.contentProvider.budgetSplitContract.itemsTagsDetailsRO#COLUMN_TAG_NAME} they are interpreted as the Values for the Objects.
     *
     * @param cursor A Cursor containing tags to convert into List of Tags.
     * @return A {@link java.util.List} of Tags containing all Tags in cursor. If the cursors row count was zero, an empty List is returned.
     * @throws IllegalArgumentException if the Cursor does not contain any rows with the Column names mentioned above.
     */
    List<Tag> tagCursorToList(Cursor cursor) {
        if (cursor == null) {
            throw new NullPointerException("The given Cursor cursor was null.");
        }
        int idIndex = cursor.getColumnIndex(budgetSplitContract.itemsTagsDetailsRO.COLUMN_TAG_ID);
        if (idIndex == -1) {
            idIndex = cursor.getColumnIndexOrThrow(BaseColumns._ID);
        }
        int nameIndex = cursor.getColumnIndex(budgetSplitContract.itemsTagsDetailsRO.COLUMN_TAG_NAME);
        if (nameIndex == -1) {
            nameIndex = cursor.getColumnIndexOrThrow(budgetSplitContract.tags.COLUMN_NAME);
        }

        ArrayList<Tag> list = new ArrayList<Tag>();
        if (cursor.getCount() > 0) {
            if (cursor.moveToFirst()) {
                while (!cursor.isAfterLast()) {
                    list.add(new Tag(cursor.getLong(idIndex), cursor.getString(nameIndex)));
                    cursor.moveToNext();
                }
            }
        }
        return list;
    }

    static class TagHelper {

        /**
         * Inflates all Tags with the Tag Custom-Layout in tags and returns a list of the inflated views.
         *
         * @param root The root of the Views added to the List.
         * @param tags The tags to inflate the views for.
         * @return A List of all inflated Views.
         */
        static List<View> TagsToCustomView(ViewGroup root, List<Tag> tags) {
            ArrayList<View> tagViews = new ArrayList<View>();
            for (Tag tag : tags) {
                tagViews.add(tag.getTagView(root));
            }
            return tagViews;
        }

        /**
         * Creates a TextView for each Tag in tags and returns a list of the views.
         *
         * @param root The root of the Views added to the List.
         * @param tags The tags to inflate the views for.
         * @return A List of all inflated Views.
         */
        static List<View> TagsToTextView(ViewGroup root, List<Tag> tags) {
            ArrayList<View> tagViews = new ArrayList<View>();
            for (Tag tag : tags) {
                tagViews.add(tag.getTagTextView(root));
            }
            return tagViews;
        }
    }


    //Methods to handle Payer
    class ItemParticipantsAdapter extends ArrayAdapter<Payer> {
        private ArrayList<Payer> payers;
        Context context;
        int layoutResourceId;
        private ArrayList<SimpleCursorAdapter> currencyAdapters = new ArrayList<SimpleCursorAdapter>();

        public ItemParticipantsAdapter(Context context, int layoutResourceId, ArrayList<Payer> payers) {
            super(context, layoutResourceId, payers);
            this.context = context;
            this.layoutResourceId = layoutResourceId;
            this.payers = payers;
        }

        @Override
        public View getView(int position, View convertView, final ViewGroup parent) {
            View row = convertView;
            PayerHolder holder = null;
            if (row == null) {
                LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
                row = inflater.inflate(layoutResourceId, parent, false);

                holder = new PayerHolder();
                holder.userName = (TextView) row.findViewById(R.id.textViewUsername);
                holder.currency = (Spinner) row.findViewById(R.id.spinnerCurrency);
                holder.value = (EditText) row.findViewById(R.id.editTextValue);
                holder.delete = (ImageButton) row.findViewById(R.id.imageButtonDelete);

                //Setup EventListener for Views.
                holder.currency.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                        Payer payer = (Payer) adapterView.getTag();
                        Cursor selectedCurrencyCursor = (Cursor) adapterView.getSelectedItem();
                        payer.currencyCode = selectedCurrencyCursor.getString(selectedCurrencyCursor.getColumnIndex(budgetSplitContract.currencies.COLUMN_CURRENCY_CODE));
                        payer.currencyId = selectedCurrencyCursor.getLong(selectedCurrencyCursor.getColumnIndex(budgetSplitContract.currencies._ID));
                        if (!payersToUpdate.contains(payer) && payersAlreadyAdded.contains(payer)) {
                            payersToUpdate.add(payer);
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {
                        Payer payer = (Payer) adapterView.getTag();
                        payer.currencyId = -1;
                        if (!payersToUpdate.contains(payer)) {
                            payersToUpdate.add(payer);
                        }
                    }
                });
                holder.delete.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        deletePayer((Payer) view.getTag());
                    }
                });
                holder.value.setOnFocusChangeListener(new MyEditTextListener(holder));
                holder.value.setOnEditorActionListener(new MyEditTextListener(holder));

                //Set Tag to get holder when recycling
                row.setTag(holder);
            } else {
                holder = (PayerHolder) row.getTag();
            }

            //Write Data to Views
            holder.userName.setText(payers.get(position).payerName);
            holder.value.setText(new DecimalFormat(",##0.00").format(payers.get(position).amountPayed));

            SimpleCursorAdapter cursorAdapter = new SimpleCursorAdapter(getContext(), //Setup Spinner
                    android.R.layout.simple_spinner_item, cursorCurrencies,
                    new String[]{budgetSplitContract.currencies.COLUMN_CURRENCY_CODE},
                    new int[]{android.R.id.text1},
                    0);
            currencyAdapters.add(cursorAdapter);
            holder.currency.setAdapter(cursorAdapter);
            //Get saved Currency and select in spinner.
            if (cursorCurrencies.getCount() > 0) {
                for (cursorCurrencies.moveToFirst(); !cursorCurrencies.isAfterLast(); cursorCurrencies.moveToNext()) {
                    if (cursorCurrencies.getInt(cursorCurrencies.getColumnIndex(budgetSplitContract.currencies._ID)) == payers.get(position).currencyId) {
                        break;
                    }
                }
            }
            holder.currency.setSelection(cursorCurrencies.getPosition(), true);
            holder.delete.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_remove));
            holder.delete.setTag(payers.get(position));
            holder.currency.setTag(payers.get(position));
            holder.value.setTag(payers.get(position));

            //If the privileges match, enable all editable views, else disable them.
            boolean payerIsMe;
            boolean enableRow;
            if (!fullAccess) {
                payerIsMe = payers.get(position).uniqueId.equals(myUniqueId);
                enableRow = payerIsMe;
            } else {
                enableRow = true;
            }
            holder.value.setEnabled(enableRow);
            holder.currency.getSelectedView().setEnabled(enableRow);             //Enable Spinner and SelectedView.
            holder.currency.setEnabled(enableRow);
            holder.delete.setEnabled(enableRow);
            return row;
        }

        void closeCursorForAllCurrencyAdapter() {
            for (SimpleCursorAdapter adapter : currencyAdapters) {
                adapter.swapCursor(null);
            }
            cursorCurrencies.close();
        }

        void swapCursorForAllCurrencyAdapter(Cursor cursor) {
            for (SimpleCursorAdapter adapter : currencyAdapters) {
                adapter.swapCursor(cursor);
            }
        }

        class PayerHolder {
            TextView userName;
            Spinner currency;
            EditText value;
            ImageButton delete;
        }

        class MyEditTextListener implements TextView.OnEditorActionListener, View.OnFocusChangeListener {

            private PayerHolder holder;

            MyEditTextListener(PayerHolder holder) {
                this.holder = holder;
            }


            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                switch (i) {
                    case EditorInfo.IME_ACTION_DONE:
                        if (textView.getText().toString().length() > 0) {
                            Double newValue = Double.parseDouble(textView.getText().toString());
                            saveValueChanges(newValue);
                            textView.clearFocus();
                            InputMethodManager mnr = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                            mnr.hideSoftInputFromWindow(textView.getWindowToken(), 0);
                            textView.setText(new DecimalFormat(",##0.00").format(newValue));
                            return true;
                        } else {
                            Toast.makeText(getApplicationContext(), getString(R.string.please_enter_value), Toast.LENGTH_SHORT).show();
                            return true;
                        }
                    default:
                        return false;
                }
            }

            @Override
            public void onFocusChange(View view, boolean b) {
                if (!b) {
                    if (((EditText) view).getText().toString().length() > 0) {
                        Double newValue = Double.parseDouble(((EditText) view).getText().toString());
                        saveValueChanges(newValue);
                        ((EditText) view).setText(new DecimalFormat(",##0.00").format(newValue));
                    } else {
                        view.requestFocus();
                        Toast.makeText(getApplicationContext(), getString(R.string.please_enter_value), Toast.LENGTH_SHORT).show();
                    }
                }
            }

            void saveValueChanges(double newValue) {
                Payer payer = (Payer) holder.value.getTag();
                payer.amountPayed = newValue;
                if (!payersToUpdate.contains(payer) && payer.rowId != -1) {
                    payersToUpdate.add(payer);
                }
            }
        }
    }

    void addPayer(Payer payer) {
        payersToAdd.add(payer);
        payerAdapter.add(payer);
    }

    void deletePayer(Payer payer) {
        if (payersToAdd.contains(payer)) {
            payersToAdd.remove(payer);
            payersToUpdate.remove(payer);
            payerAdapter.remove(payer);
        } else {
            payersToUpdate.remove(payer);
            payersToDelete.add(payer);
            payerAdapter.remove(payer);
        }
    }

    void showPayerChooserPopup(View view) {
        if (payerChooserPopup == null) {
            AlertDialog.Builder myDialogBuilder = new AlertDialog.Builder(this);
            myDialogBuilder.setTitle(getString(R.string.choose_a_payer));
            myDialogBuilder.setCursor(cursorParticipants, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    cursorParticipants.moveToPosition(i);
                    Long payerId = cursorParticipants.getLong(cursorParticipants.getColumnIndex(budgetSplitContract.participants._ID));
                    String payerName = cursorParticipants.getString(cursorParticipants.getColumnIndex(budgetSplitContract.participants.COLUMN_NAME));
                    String uniqueId = cursorParticipants.getString(cursorParticipants.getColumnIndex(budgetSplitContract.participants.COLUMN_UNIQUEID));
                    boolean isVirtual = cursorParticipants.getInt(cursorParticipants.getColumnIndex(budgetSplitContract.participants.COLUMN_ISVIRTUAL)) > 0;
                    Payer newPayer = new Payer(payerId, payerName, isVirtual, uniqueId);
                    addPayer(newPayer);
                }
            }, budgetSplitContract.participants.COLUMN_NAME);
            payerChooserPopup = myDialogBuilder.create();
        }
        payerChooserPopup.show();
    }

    class Payer {
        long rowId;
        long payerId;
        String payerName;
        boolean isVirtual;
        String uniqueId;
        long currencyId;
        String currencyCode;
        double exchangeRate;
        double amountPayed;


        public Payer(long rowId, long payerId, String payerName, boolean isVirtual, String uniqueId, long currencyId, String currencyCode, float exchangeRate, double amountPayed) {
            this.rowId = rowId;
            this.payerId = payerId;
            this.payerName = payerName;
            this.isVirtual = isVirtual;
            this.uniqueId = (uniqueId == null) ? "" : uniqueId;
            this.currencyId = currencyId;
            this.currencyCode = currencyCode;
            this.exchangeRate = exchangeRate;
            this.amountPayed = amountPayed;
        }

        public Payer(long payerId, String payerName, boolean isVirtual, String uniqueId) {
            this.payerId = payerId;
            this.payerName = payerName;
            this.isVirtual = isVirtual;
            this.uniqueId = (uniqueId == null) ? "" : uniqueId;
            this.currencyId = defaultCurrencyId;
            this.currencyCode = defaultCurrencyCode;
            this.exchangeRate = defaultCurrencyExchangeRate;
            this.rowId = -1;
        }
    }

    /**
     * Converts a Cursor of tags to a {@link java.util.List} of Tags.
     * The Cursor must at least contain the Columns {@value budgetSplitContract.itemsParticipants#COLUMN_PARTICIPANTS_ID}, {@value budgetSplitContract.itemsParticipantsDetailsRO#COLUMN_PARTICIPANT_NAME},
     * {@value budgetSplitContract.itemsParticipantsDetailsRO#COLUMN_PARTICIPANT_IS_VIRTUAL} and {@value budgetSplitContract.itemsParticipantsDetailsRO#COLUMN_PARTICIPANT_UNIQUE_ID}.
     *
     * @param cursor A Cursor containing tags to convert into List of Tags.
     * @return A {@link java.util.List} of Tags containing all Tags in cursor. If the cursors row count was zero, an empty List is returned.
     * @throws java.lang.IllegalArgumentException if the Cursor does not contain any rows with the Column names mentioned above.
     */
    List<Payer> payerCursorToList(Cursor cursor) {
        if (cursor == null) {
            throw new NullPointerException("The given Cursor cursor was null.");
        }
        int payerIdIndex = cursor.getColumnIndexOrThrow(budgetSplitContract.itemsParticipantsDetailsRO.COLUMN_PARTICIPANT_ID);
        int payerNameIndex = cursor.getColumnIndexOrThrow(budgetSplitContract.itemsParticipantsDetailsRO.COLUMN_PARTICIPANT_NAME);
        int isVirtualIndex = cursor.getColumnIndexOrThrow(budgetSplitContract.itemsParticipantsDetailsRO.COLUMN_PARTICIPANT_IS_VIRTUAL);
        int uniqueIDIndex = cursor.getColumnIndexOrThrow(budgetSplitContract.itemsParticipantsDetailsRO.COLUMN_PARTICIPANT_UNIQUE_ID);
        int currencyIdIndex = cursor.getColumnIndexOrThrow(budgetSplitContract.itemsParticipantsDetailsRO.COLUMN_CURRENCY_ID);
        int currencyCodeIndex = cursor.getColumnIndexOrThrow(budgetSplitContract.itemsParticipantsDetailsRO.COLUMN_CURRENCY_CODE);
        int exchangeRateIndex = cursor.getColumnIndexOrThrow(budgetSplitContract.itemsParticipantsDetailsRO.COLUMN_CURRENCY_EXCHANGE_RATE);
        int amountPayedIndex = cursor.getColumnIndexOrThrow(budgetSplitContract.itemsParticipantsDetailsRO.COLUMN_AMOUNT_PAYED);
        int rowid = cursor.getColumnIndexOrThrow(budgetSplitContract.itemsParticipantsDetailsRO._ID);
        ArrayList<Payer> list = new ArrayList<Payer>();
        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                list.add(new Payer(cursor.getLong(rowid),
                        cursor.getLong(payerIdIndex),
                        cursor.getString(payerNameIndex),
                        cursor.getInt(isVirtualIndex) == 1,
                        cursor.getString(uniqueIDIndex),
                        cursor.getLong(currencyIdIndex),
                        cursor.getString(currencyCodeIndex),
                        cursor.getFloat(exchangeRateIndex),
                        cursor.getDouble(amountPayedIndex)));
                cursor.moveToNext();
            }
        }
        return list;
    }


    class ExcludeItem {
        long rowid;
        long itemId;
        long participantId;
        String participantName;
        String uniqueId;
        double shareRatio;

        public ExcludeItem(long itemId, long participantId, String participantName, String uniqueId, double shareRatio) {
            this.itemId = itemId;
            this.participantId = participantId;
            this.participantName = participantName;
            this.uniqueId = uniqueId;
            this.shareRatio = shareRatio;
        }

        public ExcludeItem(long itemId, long participantId, double shareRatio) {
            this.itemId = itemId;
            this.participantId = participantId;
            this.shareRatio = shareRatio;
            if (cursorParticipants != null && cursorParticipants.getCount() > 0) {
                for (cursorParticipants.moveToFirst(); !cursorParticipants.isAfterLast(); cursorParticipants.moveToNext()) {
                    if (cursorParticipants.getLong(cursorParticipants.getColumnIndex(budgetSplitContract.participants._ID)) == participantId) {
                        participantName = cursorParticipants.getString(cursorParticipants.getColumnIndex(budgetSplitContract.participants.COLUMN_NAME));
                        uniqueId = cursorParticipants.getString(cursorParticipants.getColumnIndex(budgetSplitContract.participants.COLUMN_UNIQUEID));
                        break;
                    }
                }
            }
            this.rowid = -1;
        }

        public ExcludeItem(long rowid, long itemId, long participantId, double shareRatio) {
            this(itemId, participantId, shareRatio);
            this.rowid = rowid;
        }
    }

    List<ExcludeItem> excludeItemsCursorToList(Cursor cursor) {
        if (cursor == null) {
            throw new IllegalArgumentException("Cursor given was null.");
        } else {
            int rowIdIndex = cursor.getColumnIndex(budgetSplitContract.excludeItems.COLUMN_ROWID);
            int itemIdIndex = cursor.getColumnIndex(budgetSplitContract.excludeItems.COLUMN_ITEM_ID);
            int participantIdIndex = cursor.getColumnIndex(budgetSplitContract.excludeItems.COLUMN_PARTICIPANTS_ID);
            int shareRatioIndex = cursor.getColumnIndex(budgetSplitContract.excludeItems.COLUMN_SHARE_RATIO);
            List<ExcludeItem> result = new ArrayList<ExcludeItem>();
            if (cursor.getCount() > 0) {
                for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                    result.add(new ExcludeItem(cursor.getLong(rowIdIndex), cursor.getLong(itemIdIndex), cursor.getLong(participantIdIndex), cursor.getDouble(shareRatioIndex)));
                }
            }
            return result;
        }
    }


    class ExcludeItemsAdapter extends ArrayAdapter<ExcludeItem> {
        Context context;
        int resourceId;
        private ArrayList<ExcludeItem> excludeItems;

        public ExcludeItemsAdapter(Context context, int resource, ArrayList<ExcludeItem> objects) {
            super(context, resource, objects);
            this.context = context;
            this.resourceId = resource;
            this.excludeItems = objects;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            ExcludeItemHolder holder = null;
            if (row == null) {
                LayoutInflater inflater = getLayoutInflater();
                row = inflater.inflate(resourceId, parent, false);

                holder = new ExcludeItemHolder();
                holder.username = (TextView) row.findViewById(R.id.textViewUsername);
                holder.shareRatio = (EditText) row.findViewById(R.id.editTextShareRatio);
                holder.delete = (ImageButton) row.findViewById(R.id.imageButtonDelete);

                //Setup Listeners
                holder.shareRatio.setOnEditorActionListener(new MyEditTextWatcher(holder));
                holder.shareRatio.setOnFocusChangeListener(new MyEditTextWatcher(holder));
                holder.delete.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        deleteExcludeItem((ExcludeItem) view.getTag());
                    }
                });

                //Attach Holder as Tag to row View.
                row.setTag(holder);
            } else {
                holder = (ExcludeItemHolder) row.getTag();
            }

            //Bind Data to Views
            holder.username.setText(excludeItems.get(position).participantName);
            holder.shareRatio.setText(new DecimalFormat("##0").format(excludeItems.get(position).shareRatio * 100) + "%");
            holder.shareRatio.setTag(excludeItems.get(position));
            holder.delete.setTag(excludeItems.get(position));

            boolean enableView;
            if (!fullAccess) {
                boolean excludeItemIsMe = excludeItems.get(position).uniqueId.equals(myUniqueId);
                enableView = excludeItemIsMe;
            } else {
                enableView = true;
            }
            holder.shareRatio.setEnabled(enableView);
            holder.delete.setEnabled(enableView);

            return row;
        }

        class ExcludeItemHolder {
            TextView username;
            EditText shareRatio;
            ImageButton delete;
        }

        class MyEditTextWatcher implements TextView.OnEditorActionListener, View.OnFocusChangeListener {

            private ExcludeItemHolder holder;

            MyEditTextWatcher(ExcludeItemHolder holder) {
                this.holder = holder;
            }

            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                switch (i) {
                    case EditorInfo.IME_ACTION_DONE:
                        if (textView.getText().toString().length() > 0) {
                            Double value = Double.parseDouble(textView.getText().toString().replace("%", ""));
                            if (value >= 0 && value <= 100) {
                                saveValueChanges(value / 100);
                                textView.clearFocus();
                                InputMethodManager mnr = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                                mnr.hideSoftInputFromWindow(textView.getWindowToken(), 0);
                                textView.setText(new DecimalFormat(",##0").format(value) + "%");
                                return true;
                            } else {
                                Toast.makeText(getApplicationContext(), getString(R.string.please_enter_a_value_between_100_and_0), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(getApplicationContext(), getString(R.string.please_enter_value), Toast.LENGTH_SHORT).show();
                            return true;
                        }
                    default:
                        return false;
                }
            }

            @Override
            public void onFocusChange(View view, boolean b) {
                if (!b) {
                    if (((EditText) view).getText().toString().length() > 0) {
                        Double newValue = Double.parseDouble(((EditText) view).getText().toString().replace("%", ""));
                        if (newValue >= 0 && newValue <= 100) {
                            saveValueChanges(newValue / 100);
                            ((EditText) view).setText(new DecimalFormat(",##0").format(newValue) + "%");
                        } else {
                            view.requestFocus();
                            Toast.makeText(getApplicationContext(), getString(R.string.please_enter_a_value_between_100_and_0), Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        view.requestFocus();
                        Toast.makeText(getApplicationContext(), getString(R.string.please_enter_value), Toast.LENGTH_SHORT).show();
                    }
                }
            }

            private void saveValueChanges(double newRatio) {
                ExcludeItem excludeItem = (ExcludeItem) holder.shareRatio.getTag();
                excludeItem.shareRatio = newRatio;
                if (!excludeItemsToUpdate.contains(excludeItem) && excludeItem.rowid != -1) {
                    excludeItemsToUpdate.add(excludeItem);
                }
            }
        }
    }

    void deleteExcludeItem(ExcludeItem excludeItem) {
        if (excludeItemsToAdd.contains(excludeItem)) {
            excludeItemsToAdd.remove(excludeItem);
            excludeItemsToUpdate.remove(excludeItem);
            excludeItemsAdapter.remove(excludeItem);
        } else {
            excludeItemsToUpdate.remove(excludeItem);
            excludeItemsToDelete.add(excludeItem);
            excludeItemsAdapter.remove(excludeItem);
        }
    }

    void addExcludeItem(ExcludeItem excludeItem) {
        excludeItemsToAdd.add(excludeItem);
        excludeItemsAdapter.add(excludeItem);
    }

    void showExcludeItemPopup() {
        if (excludeItemsChooserPopup == null) {
            AlertDialog.Builder myDialogBuilder = new AlertDialog.Builder(this);
            myDialogBuilder.setTitle(getString(R.string.choose_a_payer));
            myDialogBuilder.setCursor(cursorParticipants, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    cursorParticipants.moveToPosition(i);
                    Long participantId = cursorParticipants.getLong(cursorParticipants.getColumnIndex(budgetSplitContract.participants._ID));
                    String participantName = cursorParticipants.getString(cursorParticipants.getColumnIndex(budgetSplitContract.participants.COLUMN_NAME));
                    String uniqueId = cursorParticipants.getString(cursorParticipants.getColumnIndex(budgetSplitContract.participants.COLUMN_UNIQUEID));
                    ExcludeItem newPayer = new ExcludeItem(itemId, participantId, participantName, uniqueId, 0);
                    addExcludeItem(newPayer);
                }
            }, budgetSplitContract.participants.COLUMN_NAME);
            excludeItemsChooserPopup = myDialogBuilder.create();
        }
        excludeItemsChooserPopup.show();
    }


    @Override
    protected void onDestroy() {
        getLoaderManager().destroyLoader(LOADER_ITEM_PARTICIPANTS);
        getLoaderManager().destroyLoader(LOADER_ITEM);
        getLoaderManager().destroyLoader(LOADER_TAGS);
        getLoaderManager().destroyLoader(LOADER_CURRENCIES);
        getLoaderManager().destroyLoader(LOADER_ITEM_TAGS);
        getLoaderManager().destroyLoader(LOADER_PROJECT);
        getLoaderManager().destroyLoader(LOADER_PARTICIPANTS);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        getLoaderManager().initLoader(LOADER_PROJECT, null, this);
        getLoaderManager().initLoader(LOADER_PARTICIPANTS, null, this);
        getLoaderManager().initLoader(LOADER_TAGS, null, this);
        getLoaderManager().initLoader(LOADER_CURRENCIES, null, this);
        if (!isNewItem) {
            getLoaderManager().initLoader(LOADER_ITEM, null, this);
            getLoaderManager().initLoader(LOADER_ITEM_PARTICIPANTS, null, this);
            getLoaderManager().initLoader(LOADER_ITEM_TAGS, null, this);
        }
    }
}

