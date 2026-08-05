package com.adiraimaji.customkeyboard;

import android.content.Context;

public class KeymapEditDialog
{
    public interface Callback
    {
        void select(String text);
        String validate(String text);
    }

    /** [allow_remove] shows a "Remove Keymap" button, used when editing an
     existing stored keymap (callback.select(null) means "delete it"). */
    public static void show(
            Context ctx,
            String initialText,
            boolean allow_remove,
            Callback callback)
    {
        CustomLayoutEditDialog.show(
                ctx,
                initialText,
                allow_remove,
                R.string.pref_keymap_title,
                R.string.pref_keymap_remove,
                new CustomLayoutEditDialog.Callback()
                {
                    @Override
                    public void select(String text)
                    {
                        callback.select(text);
                    }

                    @Override
                    public String validate(String text)
                    {
                        return callback.validate(text);
                    }
                });
    }
}