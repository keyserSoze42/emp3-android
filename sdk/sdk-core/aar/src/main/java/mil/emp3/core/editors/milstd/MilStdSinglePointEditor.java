package mil.emp3.core.editors.milstd;

import armyc2.c2sd.renderer.utilities.SymbolDef;
import armyc2.c2sd.renderer.utilities.UnitDef;
import mil.emp3.api.MilStdSymbol;
import mil.emp3.api.exceptions.EMP_Exception;
import mil.emp3.api.listeners.IDrawEventListener;
import mil.emp3.api.listeners.IEditEventListener;
import mil.emp3.core.editors.AbstractSinglePointEditor;
import mil.emp3.api.utils.MilStdUtilities;
import mil.emp3.mapengine.interfaces.IMapInstance;

/**
 * This class implements the editor for single point MilStd symbols.
 */
public class MilStdSinglePointEditor extends AbstractSinglePointEditor<MilStdSymbol> {
    public MilStdSinglePointEditor(IMapInstance map, MilStdSymbol feature, IEditEventListener oEventListener) throws EMP_Exception {
        super(map, feature, oEventListener, false);
        this.initializeEdit();
    }

    public MilStdSinglePointEditor(IMapInstance map, MilStdSymbol feature, IDrawEventListener oEventListener, boolean newFeature) throws EMP_Exception {
        super(map, feature, oEventListener, false, newFeature);
        this.initializeDraw();
    }

    @Override
    protected void prepareForDraw() throws EMP_Exception {
        String sSymbolCode = this.oFeature.getBasicSymbol();
        int iMilStdVersion = MilStdUtilities.geoMilStdVersionToRendererVersion(this.oFeature.getSymbolStandard());
        UnitDef oUnitDef = armyc2.c2sd.renderer.utilities.UnitDefTable.getInstance().getUnitDef(sSymbolCode, iMilStdVersion);

        if (null == oUnitDef) {
            // It may be a single point TG I.E. single point target.
            SymbolDef symbolDef =  armyc2.c2sd.renderer.utilities.SymbolDefTable.getInstance().getSymbolDef(sSymbolCode, iMilStdVersion);

            if (symbolDef.getDrawCategory() != SymbolDef.DRAW_CATEGORY_POINT) {
                throw new EMP_Exception(EMP_Exception.ErrorDetail.INVALID_PARAMETER, "Invalid symbol code.");
            }
        } else if (oUnitDef.getDrawCategory() == UnitDef.DRAW_CATEGORY_DONOTDRAW) {
            throw new EMP_Exception(EMP_Exception.ErrorDetail.INVALID_PARAMETER, "Invalid symbol code.");
        }

        super.prepareForDraw();
    }
}
