package com.vericore

/** The entry point of the application. Configures the CLI commands and executes the pipeline. */
import com.vericore.cli.AIAssistantCommand
import com.vericore.cli.ArchitectureCommand
import com.vericore.cli.ArchitectureContractCommand
import com.vericore.cli.ArchitectureDriftCommand
import com.vericore.cli.DoctorCommand
import com.vericore.cli.EngineeringContextDiffCommand
import com.vericore.cli.EngineeringContextSnapshotCommand
import com.vericore.cli.EngineeringPlanCommand
import com.vericore.cli.EvidenceGraphCommand
import com.vericore.cli.EvolutionCommand
import com.vericore.cli.ImprovedAnalyzeCommand
import com.vericore.cli.ImpactCommand
import com.vericore.cli.MainCommand
import com.vericore.cli.McpCommand
import com.vericore.cli.McpConfigCommand
import com.vericore.cli.PRIntelligenceCommand
import com.vericore.cli.PrepareCommand
import com.vericore.cli.RealityCommand
import com.vericore.cli.RepositoryQACommand
import com.vericore.cli.ServerCommand
import com.vericore.cli.SetupCommand
import com.vericore.cli.VerifyCommand
import com.github.ajalt.clikt.core.subcommands
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    try {
        MainCommand()
            .subcommands(
                ImprovedAnalyzeCommand(),
                ImpactCommand(),
                ArchitectureCommand(),
                ArchitectureDriftCommand(),
                ArchitectureContractCommand(),
                EngineeringContextSnapshotCommand(),
                EngineeringContextDiffCommand(),
                EvidenceGraphCommand(),
                RealityCommand(),
                PRIntelligenceCommand(),
                RepositoryQACommand(),
                EngineeringPlanCommand(),
                PrepareCommand(),
                VerifyCommand(),
                AIAssistantCommand(),
                EvolutionCommand(),
                ServerCommand(),
                McpCommand(),
                McpConfigCommand(),
                SetupCommand(),
                DoctorCommand()
            )
            .main(args)
    } catch (e: Throwable) {
        com.vericore.cli.ErrorHandler.handle(e)
        // ErrorHandler intentionally owns user-facing formatting. Once an exception
        // reaches this boundary, the process must still report failure to scripts/CI.
        exitProcess(1)
    }
}
