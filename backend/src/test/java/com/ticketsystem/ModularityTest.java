package com.ticketsystem;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Modularidade verificada, nao combinada.
 *
 * <p>Este teste quebra o build quando um modulo importa classe interna de outro ou
 * quando surge ciclo entre modulos. E a unica coisa que impede a estrutura por feature
 * de virar um monte de pacotes que se importam livremente.
 */
class ModularityTest {

    static final ApplicationModules MODULES = ApplicationModules.of(TicketSystemApplication.class);

    @Test
    void respeitaAsFronteirasEntreModulos() {
        MODULES.verify();
    }

    /**
     * Os diagramas sao saida de build, por isso nunca desatualizam: quem mexe na
     * estrutura regenera sem perceber, ao rodar os testes.
     */
    @Test
    void geraOsDiagramasDeModulo() {
        new Documenter(MODULES, Documenter.Options.defaults().withOutputFolder("../docs/modules"))
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml()
            .writeModuleCanvases();
    }
}
