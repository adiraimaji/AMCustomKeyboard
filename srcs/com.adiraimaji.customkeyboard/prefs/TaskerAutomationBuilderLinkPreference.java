package com.adiraimaji.customkeyboard.prefs;

import android.content.Context;
import android.content.Intent;
import androidx.preference.Preference;
import android.util.AttributeSet;

import com.adiraimaji.customkeyboard.TaskerAutomationBuilderActivity;

/** Settings row that opens [TaskerAutomationBuilderActivity] - see
 [TaskerAutomationGuideLinkPreference] for why this starts the
 Activity explicitly by class from Java rather than through a
 resource-declared &lt;intent targetPackage=.../&gt;. */
public class TaskerAutomationBuilderLinkPreference extends Preference
{
    public TaskerAutomationBuilderLinkPreference(Context ctx, AttributeSet attrs)
    {
        super(ctx, attrs);
    }

    @Override
    protected void onClick()
    {
        getContext().startActivity(new Intent(getContext(), TaskerAutomationBuilderActivity.class));
    }
}
