import { Component, OnInit, Input } from '@angular/core';
import { LoggerInfo, SystemService } from 'src/oversigt-client';
import { NotificationService } from 'src/app/services/notification.service';

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
    private notification: NotificationService
  ) { }

  ngOnInit() {
    this.ss.getLogLevels().subscribe(
      levels => {
        this.possibleLogLevels = levels;
      },
      error => {
        console.error(error);
        alert(error);
        // TODO: Error handling
      }
    );
    this.ss.getLoggers(false).subscribe(
      loggers => {
        this.loggerInfos = loggers;
      },
      error => {
        console.error(error);
        alert(error);
        // TODO: Error handling
      }
    );
  }

  setLogLevel(loggerName: string, level: string): void {
    this.ss.setLogLevel(loggerName, level).subscribe(
      ok => {
        this.loggerInfos.filter(info => info.name === loggerName)[0].level = level;
        // TODO compute effective level
        this.notification.success('Logger "' + loggerName + '" has been set to level "' + level + '".');
      },
      error => {
        console.error(error);
        alert(error);
        // TODO: Error handling
      }
    );
  }

}
