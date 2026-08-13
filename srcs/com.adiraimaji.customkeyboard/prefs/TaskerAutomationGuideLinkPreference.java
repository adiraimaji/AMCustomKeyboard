package com.adiraimaji.customkeyboard.prefs;

import android.content.Context;
import android.content.Intent;
import androidx.preference.Preference;
import android.util.AttributeSet;

import com.adiraimaji.customkeyboard.TaskerAutomationGuideActivity;

/** Settings row that opens [TaskerAutomationGuideActivity]. A plain
 Preference with a resource-declared <intent targetPackage=.../> would
 have to hardcode the app's applicationId, which breaks on the
 ".debug" build variant (applicationIdSuffix) - starting the Activity
 explicitly by class from Java always resolves to whichever package
 this build actually shipped as. */
public class TaskerAutomationGuideLinkPreference extends Preference
{
    public TaskerAutomationGuideLinkPreference(Context ctx, AttributeSet attrs)
    {
        super(ctx, attrs);
    }

    @Override
    protected void onClick()
    {
        getContext().startActivity(new Intent(getContext(), TaskerAutomationGuideActivity.class));
    }
}
