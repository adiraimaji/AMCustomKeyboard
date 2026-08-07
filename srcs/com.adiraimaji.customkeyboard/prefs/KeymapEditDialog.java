package com.adiraimaji.customkeyboard;

import android.content.Context;
import android.content.Intent;

public class KeymapEditDialog
{
    public interface Callback
    {
        void select(String text);
        String validate(String text);
    }

    public static void show(
            final Context ctx,
            String initialText,
            boolean allow_remove,
            final String keymap_name_for_builder,
            Callback callback)
    {
        CustomLayoutEditDialog.OpenInBuilder open_builder = null;
        if (keymap_name_for_builder != null)
        {
            open_builder = new CustomLayoutEditDialog.OpenInBuilder()
            {
                public void open(String current_text)
                {
                    Intent intent = new Intent(ctx, KeymapBuilderActivity.class);
                    intent.putExtra(KeymapBuilderActivity.EXTRA_EDIT_KEYMAP_NAME,
                            keymap_name_for_builder);
                    // Pass the DIALOG'S CURRENT TEXT (may include unsaved
                    // edits and/or duplicate keys the user is trying to
                    // resolve), not the last-saved version, so nothing is
                    // silently lost by opening the builder.
                    intent.putExtra(KeymapBuilderActivity.EXTRA_INITIAL_JSON_TEXT,
                            current_text);
                    ctx.startActivity(intent);
                }
            };
        }

        CustomLayoutEditDialog.show(
                ctx,
                initialText,
                allow_remove,
                R.string.pref_keymap_title,
                R.string.pref_keymap_remove,
                open_builder,
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