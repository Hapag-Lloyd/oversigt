import { Component, OnInit, Input } from '@angular/core';
import { LoggerInfo, SystemService } from 'src/oversigt-client';
import { NotificationService } from 'src/app/services/notification.service';
import { ErrorHandlerService } from 'src/app/services/error-handler.service';

@Component({
  selector: 'app-config-logs-logger',
  templateUrl: './config-logs-logger.component.html',
  styleUrls: ['./config-logs-logger.component.css']
})
export class ConfigLogsLoggerComponent implements OnInit {
  possibleLogLevels: string[] = [];
  loggerInfos: LoggerInfo[] = [];
  filter = '';

  constructor(
    private ss: SystemService,
    private notification: NotificationService,
    private errorHandler: ErrorHandlerService,
  ) { }

  ngOnInit() {
    this.ss.getLogLevels().subscribe(
      levels => {
        this.possibleLogLevels = levels;
      },
      this.errorHandler.createErrorHandler('Listing log levels')
    );
    this.ss.getLoggers(false).subscribe(
      loggers => {
        this.loggerInfos = loggers;
      },
      this.errorHandler.createErrorHandler('List loggers')
    );
  }

  setLogLevel(loggerName: string, level: string): void {
    this.ss.setLogLevel(loggerName, level).subscribe(
      ok => {
        this.loggerInfos.filter(info => info.name === loggerName)[0].level = level;
        // TODO compute effective level or reload loggers
        this.notification.success('Logger "' + loggerName + '" has been set to level "' + level + '".');
      },
      this.errorHandler.createErrorHandler('Updating log level')
    );
  }

}
