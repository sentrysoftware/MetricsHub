//go:build windows
// +build windows

package main

import (
	"fmt"
	"os"

	"golang.org/x/sys/windows/svc"

	"go.opentelemetry.io/collector/service"
)

func run(params service.CollectorSettings) error {
	if useInteractiveMode, err := checkUseInteractiveMode(); err != nil {
		return err
	} else if useInteractiveMode {
		return runInteractive(params)
	} else {
		return runService(params)
	}
}

func checkUseInteractiveMode() (bool, error) {
	// If environment variable NO_WINDOWS_SERVICE is set with any value other
	// than 0, use interactive mode instead of running as a service. This should
	// be set in case running as a service is not possible or desired even
	// though the current session is not detected to be interactive
	if value, present := os.LookupEnv("NO_WINDOWS_SERVICE"); present && value != "0" {
		return true, nil
	}

	isInteractiveSession, err := svc.IsAnInteractiveSession()
	if err != nil {
		return false, fmt.Errorf("failed to determine if we are running in an interactive session: %w", err)
	}
	return isInteractiveSession, nil
}

func runService(params service.CollectorSettings) error {

	// do not need to supply service name when startup is invoked through Service Control Manager directly
	winService := NewWindowsService(params)

	if err := svc.Run("", winService); err != nil {
		return fmt.Errorf("failed to start collector server: %w", err)
	}

	return nil
}
