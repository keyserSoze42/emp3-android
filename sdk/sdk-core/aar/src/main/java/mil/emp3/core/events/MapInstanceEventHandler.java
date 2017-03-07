package mil.emp3.core.events;

import android.util.Log;

import org.cmapi.primitives.IGeoBounds;
import org.cmapi.primitives.IGeoPosition;

import mil.emp3.api.enums.CameraEventEnum;
import mil.emp3.api.enums.LookAtEventEnum;
import mil.emp3.api.enums.MapMotionLockEnum;
import mil.emp3.api.enums.MapStateEnum;
import mil.emp3.api.enums.UserInteractionEventEnum;
import mil.emp3.api.exceptions.EMP_Exception;
import mil.emp3.api.interfaces.ICamera;
import mil.emp3.api.interfaces.ILookAt;
import mil.emp3.api.interfaces.IMap;
import mil.emp3.api.interfaces.IMapService;
import mil.emp3.api.interfaces.core.ICoreManager;
import mil.emp3.api.interfaces.core.IEventManager;
import mil.emp3.api.interfaces.core.IMapInstanceEventHandler;
import mil.emp3.api.interfaces.core.IStorageManager;
import mil.emp3.api.interfaces.core.storage.IClientMapRestoreData;
import mil.emp3.api.interfaces.core.storage.IClientMapToMapInstance;
import mil.emp3.api.utils.GeoLibrary;
import mil.emp3.api.utils.ManagerFactory;
import mil.emp3.core.storage.MapStatus;
import mil.emp3.mapengine.events.MapInstanceFeatureAddedEvent;
import mil.emp3.mapengine.events.MapInstanceFeatureRemovedEvent;
import mil.emp3.mapengine.events.MapInstanceFeatureUserInteractionEvent;
import mil.emp3.mapengine.events.MapInstanceStateChangeEvent;
import mil.emp3.mapengine.events.MapInstanceUserInteractionEvent;
import mil.emp3.mapengine.events.MapInstanceViewChangeEvent;

/**
 * This class handles map events from the map instance.
 */
public abstract class MapInstanceEventHandler extends MapStatus implements IMapInstanceEventHandler {
    private static final String TAG = MapInstanceEventHandler.class.getSimpleName();

    final private IStorageManager storageManager = ManagerFactory.getInstance().getStorageManager();
    final private IEventManager eventManager     = ManagerFactory.getInstance().getEventManager();
    final private ICoreManager coreManager       = ManagerFactory.getInstance().getCoreManager();

    @Override
    public void copy(IClientMapToMapInstance from) {
        super.copy(from);
    }

    private void processDragEvent(android.graphics.Point oPoint, IGeoPosition oEventPosition, IGeoPosition oStartPosition) {
        final double EDGE_PERCENT = 0.10;
        // We have a smart motion lock. See if we are at the edge.
        int iHeight = this.getMapViewHeight();
        int iWidth = this.getMapViewWidth();
        int iDeltaWidth = (int) (EDGE_PERCENT * (double) iWidth);
        int iDeltaHeight = (int) (EDGE_PERCENT * (double) iHeight);
        Log.d(TAG, "processDragEvent " + iHeight + " " + iWidth + " " + iDeltaHeight + " " + iDeltaWidth + " " + oPoint.x + " " + oPoint.y);

        if ((oPoint.x < iDeltaWidth) ||
                (oPoint.x > (iWidth - iDeltaWidth)) ||
                (oPoint.y < iDeltaHeight) ||
                (oPoint.y > (iHeight - iDeltaHeight))) {
            // The Drag is at an edge. We need to pan the map in that direction.
            IGeoPosition oCenter = this.getCamera();

            double dAzimuth = GeoLibrary.computeBearing(oCenter, oEventPosition);
            double dDistance = GeoLibrary.computeDistanceBetween(oCenter, oEventPosition) / 20.0; // We move 1/20 the distance.
            GeoLibrary.computePositionAt(dAzimuth, dDistance, oCenter, oCenter);
            this.getCamera().apply(false);    // This needs to be addressed TODO
        }
    }

    @Override
    public void onEvent(MapInstanceFeatureUserInteractionEvent event) {
        IMap oClientMap = this.getClientMap();
        boolean bEventConsumed = false;

        if (oClientMap != null) {
            if (this.isEditing()) {
                bEventConsumed = this.getEditor().onEvent(event);
                event.setEventConsumed(bEventConsumed);
            } else switch (event.getEvent()) {
                case DRAG:
                case DRAG_COMPLETE:
                    bEventConsumed = true;
                    break;
            }

            // There is a case where DRAG event is forwarded to the application, In EditorMode.DRAW_MODE, if user places a finger on
            // and existing feature and drags. Remember that MapInstanceFeatureUserInteractionEvent is never sent to the application
            // FeatureUserInteractionEvent is recreated and sent to the user. So only core can invoke setEventConsumed/isEventConsumed
            // on MapInstanceFeatureUserInteractionEvent. THIS IS VERY IMPORTANT. Please see processing in world-wind/PickNavigateController.

            if (!bEventConsumed) {
                eventManager.generateFeatureInteractionEvent(event.getEvent(), event.getKeys(), event.getButton(),
                        event.getFeatures(), oClientMap, event.getLocation(), event.getCoordinate(), event.getStartCoordinate());
            }

            if ((event.getEvent() == UserInteractionEventEnum.DRAG) &&
                    (this.getLockMode() == MapMotionLockEnum.SMART_LOCK) &&
                    (event.getCoordinate() != null)) {
                // If we are in smart lock mode we need to check if the event is at the eadge.
                this.processDragEvent(event.getLocation(), event.getCoordinate(), event.getStartCoordinate());
            }
        }
    }

    /**
     * If the new event is MAP_READY then redraw all the features. This enables following two
     * capabilities:
     *    Applications can create a Map without Map Instance, add overlays and features and then use
     *    swapMapEngine to install a MapInstance. Features will be redrawn.
     *    On normal swapMapEngine all features will get redrawn
     *    When activity restarts because of things like orientation changes then features will get redrawn
     * @param event
     */
    @Override
    public void onEvent(MapInstanceStateChangeEvent event) {
        IMap oClientMap = this.getClientMap();

        this.setMapState(event.getState());
        if (oClientMap != null) {
            // Map Engine is ready, redraw any features that are present on client map.
            if (event.getState() == MapStateEnum.MAP_READY) {
                storageManager.redrawAllFeatures(oClientMap);
                IClientMapRestoreData cmrd = storageManager.getRestoreData(oClientMap);
                if ((null != cmrd) && (null != cmrd.getCamera())) {
                    try {
                        if (null != cmrd.getCamera()) {
                            coreManager.setCamera(oClientMap, cmrd.getCamera(), false);
                        }
                        if (null != cmrd.getMapServiceHash()) {
                            for (IMapService mapService : cmrd.getMapServiceHash().values()) {
                                storageManager.addMapService(oClientMap, mapService);
                            }
                        }
                    } catch (EMP_Exception e) {
                        Log.e(TAG, "Cannot restore camera or mapService", e);
                    }
                } else {
                    // We are not restoring. Set the current camera.
                    try {
                        coreManager.setCamera(oClientMap, this.getCamera(), false);
                    } catch (EMP_Exception e) {
                        Log.e(TAG, "Failed to set the initial camera.", e);
                    }
                }
            }
            eventManager.generateMapStateChangeEvent(oClientMap.getState(), event.getState(), oClientMap);
        }
    }

    @Override
    public void onEvent(MapInstanceUserInteractionEvent event) {
        IMap oClientMap = this.getClientMap();
        boolean bEventConsumed = false;

        if (oClientMap != null) {
            if (this.isEditing()) {
                bEventConsumed = this.getEditor().onEvent(event);
            } else switch (event.getEvent()) {
                case DRAG:
                case DRAG_COMPLETE:
                    bEventConsumed = true;
                    break;
            }

            // Based on current implementation of Editors DRAG event will never reach application, but even if it does, it is ok, for the
            // following reason. Remember that MapInstanceUserInteractionEvent is never sent to the application
            // MapUserInteractionEvent is recreated and sent to the user. So only core can invoke setEventConsumed/isEventConsumed
            // on MapInstanceUserInteractionEvent. THIS IS VERY IMPORTANT. Please see processing in world-wind/PickNavigateController

            if (!bEventConsumed) {
                eventManager.generateMapInteractionEvent(event.getEvent(), event.getKeys(), event.getButton(),
                        oClientMap, event.getLocation(), event.getCoordinate(), event.getStartCoordinate());
            }

            if ((event.getEvent() == UserInteractionEventEnum.DRAG) &&
                    (this.getLockMode() == MapMotionLockEnum.SMART_LOCK) &&
                    (event.getCoordinate() != null)) {
                // If we are in smart lock mode we need to check if the event is at the eadge.
                this.processDragEvent(event.getLocation(), event.getCoordinate(), event.getStartCoordinate());
            }
        }
    }

    @Override
    public void onEvent(MapInstanceViewChangeEvent event) {
        IMap clientMap = this.getClientMap();
        ICamera mapCamera = this.getCamera();
        ILookAt mapLookAt = this.getLookAt();
        IGeoBounds oBounds = event.getBounds();
        storageManager.setBounds(clientMap, oBounds);
        this.setMapViewWidth(event.getMapViewWidth());
        this.setMapViewHeight(event.getMapViewHeight());

        eventManager.generateMapViewChangeEvent(event.getEvent(), mapCamera, mapLookAt, oBounds, clientMap);
        switch (event.getEvent()) {
            case VIEW_IN_MOTION:
                eventManager.generateMapCameraEvent(CameraEventEnum.CAMERA_IN_MOTION, clientMap, mapCamera, false);
                eventManager.generateCameraEvent(CameraEventEnum.CAMERA_IN_MOTION, mapCamera, false);  // TODO This needs to be addressed
                eventManager.generateLookAtEvent(LookAtEventEnum.LOOKAT_IN_MOTION, mapLookAt, false);
                break;
            case VIEW_MOTION_STOPPED:
                eventManager.generateMapCameraEvent(CameraEventEnum.CAMERA_MOTION_STOPPED, clientMap, mapCamera, false);
                eventManager.generateCameraEvent(CameraEventEnum.CAMERA_MOTION_STOPPED, mapCamera, false);  // TODO This needs to be addressed.
                eventManager.generateLookAtEvent(LookAtEventEnum.LOOKAT_MOTION_STOPPED, mapLookAt, false);
                break;
        }
    }

    @Override
    public void onEvent(MapInstanceFeatureAddedEvent event) {
        IMap oClientMap = this.getClientMap();

        if (oClientMap != null) {
            eventManager.generateMapFeatureAddedEvent(event.getEvent(), oClientMap, event.getFeature());
        }
    }

    @Override
    public void onEvent(MapInstanceFeatureRemovedEvent event) {
        IMap oClientMap = this.getClientMap();

        if (oClientMap != null) {
            eventManager.generateMapFeatureRemovedEvent(event.getEvent(), oClientMap, event.getFeature());
        }
    }
}
