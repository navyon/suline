package boeren.com.appsuline.app.bmedical.appsuline.fragments;


import android.os.Bundle;
import android.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import boeren.com.appsuline.app.bmedical.appsuline.R;

/**
 * A simple {@link Fragment} subclass.
 */
public class MeldingsstijlFragment extends android.support.v4.app.Fragment {


    public MeldingsstijlFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootview = inflater.inflate(R.layout.fragment_meldingsstijl, container, false);

        return rootview;
    }


}
