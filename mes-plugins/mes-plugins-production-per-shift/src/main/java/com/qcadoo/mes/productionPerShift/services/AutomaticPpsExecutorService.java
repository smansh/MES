package com.qcadoo.mes.productionPerShift.services;

import com.qcadoo.mes.productionPerShift.constants.PpsAlgorithm;
import com.qcadoo.mes.productionPerShift.domain.ProgressForDaysContainer;
import com.qcadoo.model.api.Entity;
import com.qcadoo.plugin.api.PluginUtils;
import com.qcadoo.plugin.api.RunIfEnabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AutomaticPpsExecutorService {

    @Autowired
    private List<AutomaticPpsService> ppsAlgorithmServcies;

    @Autowired
    private AutomaticPpsParametersService parametersService;

    public void generateProgressForDays(ProgressForDaysContainer progressForDaysContainer, Entity productionPerShift) {
        PpsAlgorithm algorithm = parametersService.getPpsAlgorithm();
        if (PpsAlgorithm.STANDARD_TECHNOLOGY == algorithm) {
            callStandardAlgorithm(progressForDaysContainer, productionPerShift, algorithm);
        } else if (PpsAlgorithm.STANDARD_TECHNOLOGY_AND_AMOUNT_OF_CHANGE == algorithm) {
            callStandardAlgorithm(progressForDaysContainer, productionPerShift, algorithm);
        } else if (PpsAlgorithm.USER == algorithm) {
            callUserAlgorithm(progressForDaysContainer, productionPerShift);
        }
    }

    private void callUserAlgorithm(ProgressForDaysContainer progressForDaysContainer, Entity productionPerShift) {
        for (AutomaticPpsService service : ppsAlgorithmServcies) {
            if (serviceEnabled(service) && isNotStandardAlgorithm(service)) {
                service.generateProgressForDays(progressForDaysContainer, productionPerShift);
            }
        }
    }

    private void callStandardAlgorithm(ProgressForDaysContainer progressForDaysContainer, Entity productionPerShift,
            PpsAlgorithm algorithm) {
        for (AutomaticPpsService service : ppsAlgorithmServcies) {
            if (serviceEnabled(service)) {
                String aClass = service.getClass().getSimpleName();
                if (algorithm.getAlgorithmClass().equalsIgnoreCase(aClass)) {
                    service.generateProgressForDays(progressForDaysContainer, productionPerShift);
                }
            }
        }
    }

    private boolean isNotStandardAlgorithm(AutomaticPpsService service) {
        return !PpsAlgorithm.STANDARD_TECHNOLOGY.getAlgorithmClass().equals(service.getClass().getSimpleName())
                && !PpsAlgorithm.STANDARD_TECHNOLOGY_AND_AMOUNT_OF_CHANGE.getAlgorithmClass().equals(
                service.getClass().getSimpleName());
    }

    private <M extends Object & AutomaticPpsService> boolean serviceEnabled(M service) {
        RunIfEnabled runIfEnabled = service.getClass().getAnnotation(RunIfEnabled.class);
        if (runIfEnabled == null) {
            return true;
        }
        for (String pluginIdentifier : runIfEnabled.value()) {
            if (!PluginUtils.isEnabled(pluginIdentifier)) {
                return false;
            }
        }
        return true;
    }

}
