package com.tomtom.online.sdk.searchalongaroute_ktx

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.common.base.Optional
import com.tomtom.online.sdk.common.location.LatLng
import com.tomtom.online.sdk.map.*
import com.tomtom.online.sdk.map.TomtomMapCallback.OnMapLongClickListener
import com.tomtom.online.sdk.routing.OnlineRoutingApi
import com.tomtom.online.sdk.routing.RoutingApi
import com.tomtom.online.sdk.routing.data.*
import com.tomtom.online.sdk.search.OnlineSearchApi
import com.tomtom.online.sdk.search.SearchApi
import com.tomtom.online.sdk.search.data.alongroute.AlongRouteSearchQueryBuilder
import com.tomtom.online.sdk.search.data.alongroute.AlongRouteSearchResponse
import com.tomtom.online.sdk.search.data.alongroute.AlongRouteSearchResult
import com.tomtom.online.sdk.search.data.reversegeocoder.ReverseGeocoderSearchQueryBuilder
import com.tomtom.online.sdk.search.data.reversegeocoder.ReverseGeocoderSearchResponse
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.observers.DisposableSingleObserver
import io.reactivex.schedulers.Schedulers


class MainActivity : AppCompatActivity(), OnMapReadyCallback, OnMapLongClickListener {
    private var tomtomMap: TomtomMap? = null
    private var searchApi: SearchApi? = null
    private var routingApi: RoutingApi? = null
    private var route: Route? = null
    private var departurePosition: LatLng? = null
    private var destinationPosition: LatLng? = null
    private var wayPointPosition: LatLng? = null
    private var departureIcon: Icon? = null
    private var destinationIcon: Icon? = null
    private var btnHelp: Button? = null
    private var btnClear: ImageButton? = null
    private var btnGasStation: ImageButton? = null
    private var btnRestaurant: ImageButton? = null
    private var btnAtm: ImageButton? = null
    private var btnSearch: ImageButton? = null
    private var editTextPois: EditText? = null
    private var dialogInProgress: Dialog? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initTomTomServices()
        initUIViews()
        setupUIViewListeners()
        disableSearchButtons()
    }

    override fun onMapReady(tomtomMap: TomtomMap) {
        this.tomtomMap = tomtomMap
        this.tomtomMap!!.isMyLocationEnabled = true
        this.tomtomMap!!.addOnMapLongClickListener(this)
        this.tomtomMap!!.markerSettings.setMarkersClustering(true)
        this.tomtomMap!!.markerSettings.markerBalloonViewAdapter = createCustomViewAdapter()
    }

    override fun onMapLongClick(latLng: LatLng) {
        if (isDeparturePositionSet && isDestinationPositionSet) {
            clearMap()
        } else {
            showDialogInProgress()
            handleLongClick(latLng)
        }
    }

    override fun onBackPressed() {
        if (!isMapCleared) {
            clearMap()
        } else {
            super.onBackPressed()
        }
    }

    private val isMapCleared: Boolean
        get() = (departurePosition == null
                ) && (destinationPosition == null
                ) && (route == null)

    private fun handleLongClick(latLng: LatLng) {
        searchApi!!.reverseGeocoding(ReverseGeocoderSearchQueryBuilder(latLng.latitude, latLng.longitude).build())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : DisposableSingleObserver<ReverseGeocoderSearchResponse?>() {
                    override fun onSuccess(response: ReverseGeocoderSearchResponse) {
                        dismissDialogInProgress()
                        processResponse(response)
                    }

                    override fun onError(e: Throwable) {
                        handleApiError(e)
                    }

                    private fun processResponse(response: ReverseGeocoderSearchResponse) {
                        if (response.hasResults()) {
                            processFirstResult(response.addresses.get(0).position)
                        } else {
                            Toast.makeText(this@MainActivity, getString(R.string.geocode_no_results), Toast.LENGTH_SHORT).show()
                        }
                    }

                    private fun processFirstResult(geocodedPosition: LatLng) {
                        if (!isDeparturePositionSet) {
                            setAndDisplayDeparturePosition(geocodedPosition)
                        } else {
                            destinationPosition = geocodedPosition
                            tomtomMap!!.removeMarkers()
                            drawRoute(departurePosition, destinationPosition)
                            enableSearchButtons()
                        }
                    }

                    private fun setAndDisplayDeparturePosition(geocodedPosition: LatLng) {
                        departurePosition = geocodedPosition
                        createMarkerIfNotPresent(departurePosition, departureIcon)
                    }
                })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        tomtomMap!!.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun initTomTomServices() {
        (supportFragmentManager.findFragmentById(R.id.mapFragment) as MapFragment?)?.getAsyncMap(this)
        searchApi = OnlineSearchApi.create(this)
        routingApi = OnlineRoutingApi.create(this)
    }

    private fun initUIViews() {
        departureIcon = Icon.Factory.fromResources(this@MainActivity, R.drawable.ic_map_route_departure)
        destinationIcon = Icon.Factory.fromResources(this@MainActivity, R.drawable.ic_map_route_destination)
        editTextPois = findViewById(R.id.edittext_main_poisearch)
        btnAtm = findViewById(R.id.btn_main_atm)
        btnHelp = findViewById(R.id.btn_main_help)
        btnClear = findViewById(R.id.btn_main_clear)
        btnGasStation = findViewById(R.id.btn_main_gasstation)
        btnRestaurant = findViewById(R.id.btn_main_restaurant)
        btnSearch = findViewById(R.id.btn_main_poisearch)
        dialogInProgress = Dialog(this)
        dialogInProgress!!.setContentView(R.layout.dialog_in_progress)
        dialogInProgress!!.setCanceledOnTouchOutside(false)
    }

    private fun setupUIViewListeners() {
        val searchButtonListener: View.OnClickListener = searchButtonListener
        btnSearch!!.setOnClickListener(searchButtonListener)
        btnGasStation!!.setOnClickListener(searchButtonListener)
        btnRestaurant!!.setOnClickListener(searchButtonListener)
        btnAtm!!.setOnClickListener(searchButtonListener)
        btnHelp!!.setOnClickListener { v: View? ->
            val intent = Intent(this@MainActivity, HelpActivity::class.java)
            startActivity(intent)
        }
        btnClear!!.setOnClickListener { clearMap() }
        editTextPois!!.addTextChangedListener(object : BaseTextWatcher() {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                deselectShortcutButtons()
            }
        })
    }

    private val searchButtonListener: View.OnClickListener
        get() {
            return object : View.OnClickListener {
                override fun onClick(v: View) {
                    handleSearchClick(v)
                }

                private fun handleSearchClick(v: View) {
                    if (isRouteSet) {
                        val description: Optional<CharSequence> = Optional.fromNullable(v.contentDescription)
                        if (description.isPresent) {
                            editTextPois!!.setText(description.get())
                            deselectShortcutButtons()
                            v.isSelected = true
                        }
                        if (isWayPointPositionSet) {
                            tomtomMap!!.clear()
                            drawRoute(departurePosition, destinationPosition)
                        }
                        val textToSearch: String = editTextPois!!.text.toString()
                        if (textToSearch.isNotEmpty()) {
                            tomtomMap!!.removeMarkers()
                            searchAlongTheRoute(route, textToSearch)
                        }
                    }
                }

                private val isRouteSet: Boolean
                    get() {
                        return route != null
                    }

                private val isWayPointPositionSet: Boolean
                    get() {
                        return wayPointPosition != null
                    }

                private fun searchAlongTheRoute(route: Route?, textToSearch: String) {
                    val maxDetourTime = 1000
                    val queryLimit = 10
                    disableSearchButtons()
                    showDialogInProgress()
                    searchApi!!.alongRouteSearch(AlongRouteSearchQueryBuilder(textToSearch, route!!.coordinates, maxDetourTime)
                            .withLimit(queryLimit)
                            .build())
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(object : DisposableSingleObserver<AlongRouteSearchResponse?>() {
                                override fun onSuccess(response: AlongRouteSearchResponse) {
                                    displaySearchResults(response.results)
                                    dismissDialogInProgress()
                                    enableSearchButtons()
                                }

                                private fun displaySearchResults(results: List<AlongRouteSearchResult>) {
                                    if (results.isNotEmpty()) {
                                        for (result: AlongRouteSearchResult in results) {
                                            createAndDisplayCustomMarker(result.position, result)
                                        }
                                        tomtomMap!!.zoomToAllMarkers()
                                    } else {
                                        Toast.makeText(this@MainActivity, String.format(getString(R.string.no_search_results), textToSearch), Toast.LENGTH_LONG).show()
                                    }
                                }

                                private fun createAndDisplayCustomMarker(position: LatLng, result: AlongRouteSearchResult) {
                                    val address: String = result.address.freeformAddress
                                    val poiName: String = result.poi.name
                                    val markerBalloonData = BaseMarkerBalloon()
                                    markerBalloonData.addProperty(getString(R.string.poi_name_key), poiName)
                                    markerBalloonData.addProperty(getString(R.string.address_key), address)
                                    val markerBuilder: MarkerBuilder = MarkerBuilder(position)
                                            .markerBalloon(markerBalloonData)
                                            .shouldCluster(true)
                                    tomtomMap!!.addMarker(markerBuilder)
                                }

                                override fun onError(e: Throwable) {
                                    handleApiError(e)
                                    enableSearchButtons()
                                }
                            })
                }
            }
        }

    private fun disableSearchButtons() {
        btnSearch!!.isEnabled = false
        btnGasStation!!.isEnabled = false
        btnAtm!!.isEnabled = false
        btnRestaurant!!.isEnabled = false
        btnClear!!.isEnabled = false
    }

    private fun enableSearchButtons() {
        btnSearch!!.isEnabled = true
        btnGasStation!!.isEnabled = true
        btnAtm!!.isEnabled = true
        btnRestaurant!!.isEnabled = true
        btnClear!!.isEnabled = true
    }

    private fun deselectShortcutButtons() {
        btnGasStation!!.isSelected = false
        btnRestaurant!!.isSelected = false
        btnAtm!!.isSelected = false
    }

    private fun showDialogInProgress() {
        if (!dialogInProgress!!.isShowing) {
            dialogInProgress!!.show()
        }
    }

    private fun dismissDialogInProgress() {
        if (dialogInProgress!!.isShowing) {
            dialogInProgress!!.dismiss()
        }
    }

    private fun createCustomViewAdapter(): SingleLayoutBalloonViewAdapter {
        return object : SingleLayoutBalloonViewAdapter(R.layout.marker_custom_balloon) {
            override fun onBindView(view: View, marker: Marker, baseMarkerBalloon: BaseMarkerBalloon) {
                val btnAddWayPoint: Button = view.findViewById(R.id.btn_balloon_waypoint)
                val textViewPoiName: TextView = view.findViewById(R.id.textview_balloon_poiname)
                val textViewPoiAddress: TextView = view.findViewById(R.id.textview_balloon_poiaddress)
                textViewPoiName.text = baseMarkerBalloon.getStringProperty(applicationContext.getString(R.string.poi_name_key))
                textViewPoiAddress.text = baseMarkerBalloon.getStringProperty(applicationContext.getString(R.string.address_key))
                btnAddWayPoint.setOnClickListener(object : View.OnClickListener {
                    override fun onClick(v: View) {
                        setWayPoint(marker)
                    }

                    private fun setWayPoint(marker: Marker) {
                        wayPointPosition = marker.position
                        tomtomMap!!.clearRoute()
                        drawRouteWithWayPoints(departurePosition, destinationPosition, arrayOf(wayPointPosition!!))
                        marker.deselect()
                    }
                })
            }
        }
    }

    private fun createMarkerIfNotPresent(position: LatLng?, icon: Icon?) {
        val optionalMarker: Optional<Marker> = tomtomMap!!.findMarkerByPosition(position)
        if (!optionalMarker.isPresent) {
            tomtomMap!!.addMarker(MarkerBuilder((position)!!)
                    .icon(icon))
        }
    }

    private fun clearMap() {
        tomtomMap!!.clear()
        departurePosition = null
        destinationPosition = null
        route = null
        disableSearchButtons()
        editTextPois!!.text.clear()
    }

    private fun handleApiError(e: Throwable) {
        dismissDialogInProgress()
        Toast.makeText(this@MainActivity, getString(R.string.api_response_error, e.localizedMessage), Toast.LENGTH_LONG).show()
    }

    private fun createRouteQuery(start: LatLng?, stop: LatLng?, wayPoints: Array<LatLng>?): RouteQuery {
        return if ((wayPoints != null)) RouteQueryBuilder(start, stop).withWayPoints(wayPoints).withRouteType(RouteType.FASTEST).build() else RouteQueryBuilder(start, stop).withRouteType(RouteType.FASTEST).build()
    }

    private fun drawRoute(start: LatLng?, stop: LatLng?) {
        wayPointPosition = null
        drawRouteWithWayPoints(start, stop, null)
    }

    private fun drawRouteWithWayPoints(start: LatLng?, stop: LatLng?, wayPoints: Array<LatLng>?) {
        val routeQuery: RouteQuery = createRouteQuery(start, stop, wayPoints)
        showDialogInProgress()
        routingApi!!.planRoute(routeQuery)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : DisposableSingleObserver<RouteResponse?>() {
                    override fun onSuccess(routeResult: RouteResponse) {
                        dismissDialogInProgress()
                        displayRoutes(routeResult.routes)
                        tomtomMap!!.displayRoutesOverview()
                    }

                    private fun displayRoutes(routes: List<FullRoute>) {
                        for (fullRoute: FullRoute in routes) {
                            route = tomtomMap!!.addRoute(RouteBuilder(
                                    fullRoute.coordinates).startIcon(departureIcon).endIcon(destinationIcon))
                        }
                    }

                    override fun onError(e: Throwable) {
                        handleApiError(e)
                        clearMap()
                    }
                })
    }

    private val isDestinationPositionSet: Boolean
        get() {
            return destinationPosition != null
        }

    private val isDeparturePositionSet: Boolean
        get() {
            return departurePosition != null
        }

    private abstract inner class BaseTextWatcher : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun afterTextChanged(s: Editable) {}
    }
}
