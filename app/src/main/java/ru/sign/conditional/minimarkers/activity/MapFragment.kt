package ru.sign.conditional.minimarkers.activity

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PointF
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKit
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.layers.GeoObjectTapEvent
import com.yandex.mapkit.layers.GeoObjectTapListener
import com.yandex.mapkit.layers.ObjectEvent
import com.yandex.mapkit.map.*
import com.yandex.mapkit.map.Map
import com.yandex.mapkit.user_location.UserLocationLayer
import com.yandex.mapkit.user_location.UserLocationObjectListener
import com.yandex.mapkit.user_location.UserLocationView
import com.yandex.runtime.image.ImageProvider
import kotlinx.coroutines.launch
import ru.sign.conditional.minimarkers.R
import ru.sign.conditional.minimarkers.databinding.FragmentMapBinding
import ru.sign.conditional.minimarkers.util.viewBinding
import ru.sign.conditional.minimarkers.viewmodel.MapViewModel

class MapFragment :
    Fragment(R.layout.fragment_map),
    UserLocationObjectListener,
    GeoObjectTapListener,
    InputListener
{
    private val viewScope
        get() = viewLifecycleOwner.lifecycleScope
    private val mapViewModel: MapViewModel by activityViewModels()
    private val binding by viewBinding(FragmentMapBinding::bind)
    private val nativeLand = Point(56.594888, 84.899818)
    private lateinit var mapKitInstance: MapKit
    private lateinit var imageProvider: ImageProvider
    private var userPlaceMark: Point? = null
    private var markers = mutableListOf<PlacemarkMapObject>()
    private lateinit var placemarkCollection: MapObjectCollection
    private var editingMarker: PlacemarkMapObject? = null
    private lateinit var userLocationLayer: UserLocationLayer
    private val requestPermissionsLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            // Если пользователь дал свое разрешение на геолокацию,
            // то можно обработать его данные
            if (isGranted)
                afterGrantedPermissions()
            else
                Toast.makeText(
                    requireContext(),
                    getString(R.string.permission_denied),
                    Toast.LENGTH_LONG
                ).show()
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initMapKit()
        super.onViewCreated(view, savedInstanceState)
        viewScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                initViews()
                setupListeners()
                requestLocationPermission()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        placemarkCollection = binding.mapView.map.mapObjects.addCollection()
        mapKitInstance.onStart()
        binding.mapView.onStart()
    }

    override fun onStop() {
        binding.mapView.onStop()
        mapKitInstance.onStop()
        super.onStop()
    }

    private fun initMapKit() {
        MapKitFactory.initialize(requireActivity())
        mapKitInstance = MapKitFactory.getInstance()
    }

    private fun initViews() {
        imageProvider = ImageProvider.fromBitmap(
            requireNotNull(AppCompatResources.getDrawable(
                requireContext(),
                R.drawable.ic_bookmark
            )).let {
                it.setTint(resources.getColor(R.color.dirty_yellow, requireContext().theme))
                it.toBitmap(
                    width = it.intrinsicWidth,
                    height = it.intrinsicHeight,
                    config = Bitmap.Config.ARGB_8888
                )
            }
        )
        binding.mapView.map.apply {
            isNightModeEnabled = true
            moveCamera(point = nativeLand)
        }
        addUserMarker(
            point = nativeLand,
            text = getString(R.string.seversk),
            description = getString(R.string.native_land)
        )
    }

    private fun setupListeners() {
        mapViewModel.placeName.observe(viewLifecycleOwner) {
            if (it.isNotBlank()) {
                addUserMarker(point = userPlaceMark!!, text = it)
                mapViewModel.clear()
            }
        }
        mapViewModel.shouldRemove.observe(viewLifecycleOwner) {
            if (it) {
                editingMarker?.let { marker ->
                    placemarkCollection.remove(marker)
                    markers.remove(marker)
                }
                mapViewModel.clear()
            } else {
                userPlaceMark = null
                editingMarker = null
            }
        }
        binding.mapView.map.apply {
            addTapListener(this@MapFragment)
            addInputListener(this@MapFragment)
        }
    }

    private fun addUserMarker(point: Point, text: String, description: String? = null) {
        if (markers.remove(editingMarker))
            placemarkCollection.remove(editingMarker!!)
        val marker =
            placemarkCollection.addPlacemark(point).apply {
                moveCamera(point)
                setIcon(imageProvider)
                setText(text)
                setTextStyle(TextStyle().apply {
                    placement = TextStyle.Placement.BOTTOM
                })
                userData = description ?: text
                addTapListener { mapObject, point ->
                    moveCamera(point)
                    mapObject.userData?.let {
                        Toast.makeText(
                            requireContext(),
                            it.toString(),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    editMarker(this)
                    true
                }
            }
        markers.add(marker)
    }

    private fun moveCamera(point: Point) {
        binding.mapView.map.move(
            CameraPosition(point,17F, 0F, 0F),
            Animation(Animation.Type.LINEAR, 1F),
            null
        )
    }

    private fun editMarker(mapObject: PlacemarkMapObject) {
        editingMarker = mapObject
        userPlaceMark = mapObject.geometry
        mapViewModel.setEditName(mapObject.userData.toString())
        PlacemarkDialogFragment().show(
            childFragmentManager,
            PlacemarkDialogFragment.PLACEMARK_TAG
        )
    }

    private fun requestLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> afterGrantedPermissions()
            /*shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ->*/
                // Данная инструкция взята из yandex-demo и по сути дублирует
                // инструкцию в ветке else, только здесь не обрабатывается результат
                // запроса разрешения
                // Поэтому я вообще решил эту ветку (shouldShow...) исключить
                /*ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    PackageManager.GET_PERMISSIONS
                )*/
            else ->
                requestPermissionsLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun afterGrantedPermissions() {
        mapKitInstance.apply {
            resetLocationManagerToDefault()
            userLocationLayer = createUserLocationLayer(binding.mapView.mapWindow)
        }
        userLocationLayer.apply {
            isVisible = true
            isHeadingEnabled = true
        }
        userLocationLayer.setObjectListener(this@MapFragment)
    }

    override fun onObjectAdded(userLocationView: UserLocationView) {
        val anchorWidth = binding.mapView.width * 0.5F
        val anchorHeight = binding.mapView.height * 0.5F
        userLocationLayer.setAnchor(
            PointF(anchorWidth, anchorHeight),
            PointF(anchorWidth, anchorHeight * 1.5F)
        )
    }

    override fun onObjectRemoved(p0: UserLocationView) {}

    override fun onObjectUpdated(p0: UserLocationView, p1: ObjectEvent) {}

    override fun onObjectTap(event: GeoObjectTapEvent): Boolean {
        val selectionMetadata =
            event
                .geoObject
                .metadataContainer
                .getItem(GeoObjectSelectionMetadata::class.java)
        selectionMetadata?.let {
            binding.mapView.map.selectGeoObject(
                it.id,
                it.layerId
            )
        }
        return selectionMetadata != null
    }

    override fun onMapTap(map: Map, point: Point) {
        binding.mapView.map.deselectGeoObject()
    }

    override fun onMapLongTap(map: Map, point: Point) {
        mapViewModel.clear()
        userPlaceMark = point
        PlacemarkDialogFragment().show(
            childFragmentManager,
            PlacemarkDialogFragment.PLACEMARK_TAG
        )
    }
}