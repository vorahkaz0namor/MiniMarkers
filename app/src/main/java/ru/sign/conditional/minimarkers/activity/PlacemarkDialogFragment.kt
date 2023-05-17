package ru.sign.conditional.minimarkers.activity

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.widget.EditText
import androidx.fragment.app.*
import ru.sign.conditional.minimarkers.R
import ru.sign.conditional.minimarkers.viewmodel.MapViewModel

class PlacemarkDialogFragment : DialogFragment() {
    private val mapViewModel: MapViewModel by activityViewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val placemarkText =
            EditText(requireContext()).apply {
                inputType = InputType.TYPE_CLASS_TEXT
                setText(mapViewModel.editName)
            }
        return AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.place_name))
            .setView(placemarkText)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                if (validation(placemarkText.text))
                    mapViewModel.setPlaceName(placemarkText.text.toString().trim())
            }
            .setNegativeButton(getString(R.string.remove)) { _, _ ->
                mapViewModel.shouldRemove()
            }
            .setNeutralButton(getString(R.string.cancel)) { _, _ ->
                mapViewModel.clear()
            }
            .create()
    }

    private fun validation(text: Editable) =
        (text.isNotBlank() && text.toString().trim() != mapViewModel.placeName.value)

    companion object {
        const val PLACEMARK_TAG = "PlacemarkDialog"
    }
}