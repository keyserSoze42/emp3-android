package mil.emp3.test.emp3vv.optItems;

import android.app.Activity;

import mil.emp3.api.enums.MapGridTypeEnum;
import mil.emp3.api.interfaces.IMap;
import mil.emp3.test.emp3vv.common.ExecuteTest;
import mil.emp3.test.emp3vv.common.OptItemBase;

/**
 * This class implements the map grid MGRS capability of EMP.
 */

public class MapGridMGRSAction extends OptItemBase {
    private static String TAG = MapGridMGRSAction.class.getSimpleName();

    public MapGridMGRSAction(Activity activity, IMap map1, IMap map2) {
        super(activity, map1, map2, TAG, true);
    }

    @Override
    public void run() {
        int iMap = ExecuteTest.getCurrentMap();

        if (iMap == -1) {
            return;
        }

        maps[iMap].setGridType(MapGridTypeEnum.MGRS);
    }
}
