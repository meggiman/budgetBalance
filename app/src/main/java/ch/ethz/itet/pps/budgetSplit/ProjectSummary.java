package ch.ethz.itet.pps.budgetSplit;


import android.app.LoaderManager;
import android.content.ContentUris;
import android.content.CursorLoader;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.app.Fragment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.util.ArrayList;

import ch.ethz.itet.pps.budgetSplit.contentProvider.budgetSplitContract;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ProjectSummary#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ProjectSummary extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String PROJECT_URI = "projectUri";

    // TODO: Rename and change types of parameters
    private Uri projectUri;

    // Loader ID's
    static final int LOADER_PARTICIPANTS = 0;
    private boolean participantsFinished = false;
    static final int LOADER_ITEMS = 1;
    private boolean itemsFinished = false;
    static final int LOADER_TAG_IDS = 2;
    private boolean tagsFinished = false;
    static final int LOADER_PARTICIPANT_NAMES = 3;
    private boolean namesFinished = false;
    static final int LOADER_TAG_NAMES = 4;
    private boolean tagNamesFinished = false;

    // memory for all the loaded Data
    ArrayList<Long> participantIds;
    ArrayList<Long> tagIds;
    ArrayList<ArrayList<Long>> tagIdsMatrix;
    ArrayList<String> participantNames;
    ArrayList<String> participantExpence;
    ArrayList<String> participantTags;
    // Fills the Listview Adapter
    ParticipantTagsLinker[] data;

    // GUI elements
    View mainView;
    ProgressBar progressBar;
    TextView expences;
    TextView expences1;
    TextView nrOfItems;
    TextView nrOfItems1;
    ListView list;
    Button transactions;


    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param contentUri Parameter 1.
     * @return A new instance of fragment ProjectSummary.
     */
    // TODO: Rename and change types and number of parameters
    public static ProjectSummary newInstance(Uri contentUri) {
        ProjectSummary fragment = new ProjectSummary();
        Bundle args = new Bundle();
        args.putParcelable(PROJECT_URI, contentUri);
        fragment.setArguments(args);
        return fragment;
    }

    public ProjectSummary() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            projectUri = getArguments().getParcelable(PROJECT_URI);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        mainView = inflater.inflate(R.layout.fragment_project_summary, container, false);
        progressBar = (ProgressBar) mainView.findViewById(R.id.summaryProgressBar);
        //getLoaderManager().initLoader(LOADER_PARTICIPANTS, null, this);
        //getLoaderManager().initLoader(LOADER_ITEMS, null, this);
        //Initialize Loader who need participant Ids List

        transactions = (Button) mainView.findViewById(R.id.fragment_summary_button);
        nrOfItems1 = (TextView) mainView.findViewById(R.id.fragment_summary_textview_nr_items);
        expences1 = (TextView) mainView.findViewById(R.id.summary_listview_tags);

        // Hack to debug GUI untill 131
        data = new ParticipantTagsLinker[]{
                new ParticipantTagsLinker("Chrissy", "Dublos, Kitsch", "20 CHF"),
                new ParticipantTagsLinker("Manu", "Barbies, Disney, Chäs", "213.45 CHF"),
                new ParticipantTagsLinker("Jeff", "", "456.8 CHF")
        };

        // Set GUI elements
        ParticipantTagsLinkerAdapter adapter = new ParticipantTagsLinkerAdapter(getActivity(), R.layout.fragment_project_summary_listview_row, data);
        list = (ListView) mainView.findViewById(R.id.fragment_summary_listview);
        list.setAdapter(adapter);

        expences = (TextView) mainView.findViewById(R.id.totalExpenses1);
        expences.setText("500.00 CHF");
        nrOfItems = (TextView) mainView.findViewById(R.id.nr_of_Items);
        nrOfItems.setText("23");

        //Hide Progressbar
//        ProgressBar progressBar = (ProgressBar) getView().findViewById(R.id.summaryProgressBar);
        //   progressBar.setVisibility(View.GONE);


        return mainView;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        //Show progressbar while Loading
        progressBar.setVisibility(View.VISIBLE);
        String[] projection;
        String[] selectionArgs;
        String selection;
        String column;

        Long projectId = (ContentUris.parseId(projectUri));

        switch (i) {
            case LOADER_PARTICIPANTS:
                projection = new String[]{budgetSplitContract.projectParticipants.COLUMN_PARTICIPANTS_ID};
                column = new String(budgetSplitContract.projectParticipants.COLUMN_PROJECTS_ID);
                String id = new String();
                id = projectId.toString();
                selection = new String(column + " = " + id);
                String sortOrder = new String(budgetSplitContract.projectParticipants.COLUMN_PARTICIPANTS_ID + " DESC");
                return new CursorLoader(getActivity(), budgetSplitContract.projectParticipants.CONTENT_URI, projection, selection, null, sortOrder);

            case LOADER_PARTICIPANT_NAMES:
                //     while(!participantsFinished){
                //     this.wait();
                //        }
                //    }
                projection = new String[]{budgetSplitContract.participants.COLUMN_NAME};
                column = new String(budgetSplitContract.participants._ID);
                selection = new String(column + " = ?");
                selectionArgs = new String[participantIds.size() - 1];
                i = 0;
                int size = participantIds.size();
                for (i = 0; i < size - 1; i++) {
                    selectionArgs[i] = participantIds.get(i).toString();
                }
                return new CursorLoader(getActivity(), budgetSplitContract.participants.CONTENT_URI, projection, selection, selectionArgs, null);

            case LOADER_TAG_IDS:
                //    while(!participantsFinished){
                //      try{  Thread.sleep(100);}
                //    catch (InterruptedException e){
                //      Log.d("hello","interrupted");
                //}

                projection = new String[]{budgetSplitContract.tagFilter.COLUMN_TAG_ID, budgetSplitContract.tagFilter.COLUMN_PARTICIPANTS_ID};
                column = new String(budgetSplitContract.tagFilter.COLUMN_PARTICIPANTS_ID);
                selection = new String(column + " = ?");
                selectionArgs = new String[participantIds.size()];
                for (i = 0; i < participantIds.size(); i++) {
                    selectionArgs[i] = participantIds.get(i).toString();
                }
                String sortOrder1 = new String(budgetSplitContract.tagFilter.COLUMN_PARTICIPANTS_ID + " DESC");
                return new CursorLoader(getActivity(), budgetSplitContract.tagFilter.CONTENT_URI, projection, selection, selectionArgs, sortOrder1);

            case LOADER_TAG_NAMES:
                projection = new String[]{budgetSplitContract.tags.COLUMN_NAME, budgetSplitContract.tags._ID};
                column = new String(budgetSplitContract.tags._ID);
                selection = new String(column + " = ?");
                selectionArgs = new String[tagIds.size()];
                for (i = 0; i < tagIds.size(); i++) {
                    selectionArgs[i] = participantIds.get(i).toString();
                }
                return new CursorLoader(getActivity(), budgetSplitContract.tags.CONTENT_URI, projection, selection, selectionArgs, null);

            case LOADER_ITEMS:
                projection = new String[]{
                        budgetSplitContract.itemsDetailsRO.COLUMN_ITEM_PRICE};
                selection = budgetSplitContract.itemsDetailsRO.COLUMN_PROJECT_ID + " = ?";
                selectionArgs = new String[]{projectUri.getLastPathSegment()};
                return new CursorLoader(getActivity(), budgetSplitContract.itemsDetailsRO.CONTENT_URI, projection, selection, selectionArgs, null);
            default:
                throw new IllegalArgumentException("Unknown Loader");

        }
    }


    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        switch (cursorLoader.getId()) {
            case LOADER_PARTICIPANTS:
                //Fill participant Ids List
                participantIds = new ArrayList<Long>();
                if (cursor.getCount() > 0) {
                    for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                        participantIds.add(cursor.getLong(cursor.getColumnIndex(budgetSplitContract.projectParticipants.COLUMN_PARTICIPANTS_ID)));
                    }
                }
                participantsFinished = true;
                // Deactivated untill Database fix
                //getLoaderManager().initLoader(LOADER_PARTICIPANT_NAMES, null, this);
                //getLoaderManager().initLoader(LOADER_TAG_IDS, null, this);
                break;

            case LOADER_PARTICIPANT_NAMES:
                // Fill participant Names
                participantNames = new ArrayList<String>();
                if (cursor.getCount() > 0) {
                    for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                        participantNames.add(cursor.getString(cursor.getColumnIndex(budgetSplitContract.participants.COLUMN_NAME)));
                    }
                }
                namesFinished = true;
                break;


            case LOADER_TAG_IDS:
                // Shows you which Tags correspond to which participant
                tagIdsMatrix = new ArrayList<ArrayList<Long>>();
                ArrayList<Long> memory = new ArrayList<Long>();
                // Just gives out all the ID's of the Tags
                tagIds = new ArrayList<Long>();
                if (cursor.getCount() > 0) {
                    for (cursor.moveToFirst(); cursor.isAfterLast(); cursor.moveToNext()) {
                        long previous = cursor.getLong(cursor.getColumnIndex(budgetSplitContract.tagFilter.COLUMN_PARTICIPANTS_ID));
                        while (cursor.getLong(cursor.getColumnIndex(budgetSplitContract.tagFilter.COLUMN_PARTICIPANTS_ID)) == previous) {
                            memory.add(cursor.getLong(cursor.getColumnIndex(budgetSplitContract.tagFilter.COLUMN_TAG_ID)));
                        }
                        tagIdsMatrix.add(memory);
                        memory.clear();
                    }
                    for (cursor.moveToFirst(); cursor.isAfterLast(); cursor.moveToNext()) {
                        tagIds.add(cursor.getLong(cursor.getColumnIndex(budgetSplitContract.tagFilter.COLUMN_TAG_ID)));
                    }
                }
                tagsFinished = true;
                // Deactivated untill Database fix
                // getLoaderManager().initLoader(LOADER_TAG_NAMES,null,this);
                break;

            case LOADER_TAG_NAMES:
                // Store tag names directly as string into ParticipantTagLinker
                int capacity;
                if (participantNames != null && participantNames.size() > 0) {
                    capacity = participantNames.size();
                } else {
                    capacity = 0;
                }
                String tags = null;
                ArrayList<Long> taglist;
                data = new ParticipantTagsLinker[capacity];
                if (cursor.getCount() > 0) {
                    for (int i = 0; i < capacity; i++) {
                        data[i].name = participantNames.get(i);
                        taglist = tagIdsMatrix.get(i);
                        for (int j = 0; j < taglist.size(); j++) {
                            // Iterate to get the right Tag Name
                            for (cursor.moveToFirst(); (cursor.getLong(cursor.getColumnIndex(budgetSplitContract.tags._ID)) != tagIds.get(j)); cursor.moveToNext()) {
                            }
                            // generate tags String
                            tags = tags + " " + getString(cursor.getColumnIndex(budgetSplitContract.tags.COLUMN_NAME));
                        }
                        data[i].tags = tags;
                        tags = new String();
                    }
                }
                tagNamesFinished = true;
                break;

            case LOADER_ITEMS:
                // Sum up all the item Costs for the Expences Display
                double finalExpenses = 0;
                if (cursor.getCount() > 0) {
                    for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                        finalExpenses += cursor.getDouble(cursor.getColumnIndex(budgetSplitContract.itemsDetailsRO.COLUMN_ITEM_PRICE));
                    }
                }
                SharedPreferences preference = PreferenceManager.getDefaultSharedPreferences(getActivity());
                long currencyId = Long.parseLong(preference.getString(getString(R.string.pref_default_currency), "-1"));
                Uri currencyUri = ContentUris.withAppendedId(budgetSplitContract.currencies.CONTENT_URI, currencyId);
                Cursor cursor1 = getActivity().getContentResolver().query(currencyUri, budgetSplitContract.currencies.PROJECTION_ALL, null, null, null);
                cursor1.moveToFirst();
                float exchangeRate = cursor1.getFloat(cursor1.getColumnIndex(budgetSplitContract.currencies.COLUMN_EXCHANGE_RATE));
                String currencyCode = cursor1.getString(cursor1.getColumnIndex(budgetSplitContract.currencies.COLUMN_CURRENCY_CODE));
                finalExpenses = finalExpenses * exchangeRate;
                expences = (TextView) getView().findViewById(R.id.totalExpenses1);
                expences.setText(new DecimalFormat(",##0.00").format(finalExpenses) + " " + currencyCode);
                nrOfItems = (TextView) getView().findViewById(R.id.nr_of_Items);
                nrOfItems.setText(Integer.toString(cursor.getCount()));
                itemsFinished = true;
                break;

            default:
                throw new IllegalArgumentException("Unknown Loader");

        }

        if (participantsFinished && tagsFinished && namesFinished && itemsFinished && tagNamesFinished) {
            // Set GUI elements
            ParticipantTagsLinkerAdapter adapter = new ParticipantTagsLinkerAdapter(getActivity(), R.layout.fragment_project_summary_listview_row, data);
            list = (ListView) mainView.findViewById(R.id.fragment_summary_listview);
            list.setAdapter(adapter);

            //Hide Progressbar
            ProgressBar progressBar = (ProgressBar) getView().findViewById(R.id.summaryProgressBar);
            progressBar.setVisibility(View.GONE);
        }


    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {

    }


}