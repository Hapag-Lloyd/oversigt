import { Component, OnInit } from '@angular/core';
import { EventSourceService, SystemService } from 'src/oversigt-client';
import { NotificationService } from 'src/app/services/notification.service';
import { ClrLoadingState } from '@clr/angular';
import { ErrorHandlerService } from 'src/app/services/error-handler.service';

@Component({
  selector: 'app-config-server',
  templateUrl: './config-server.component.html',
  styleUrls: ['./config-server.component.css']
})
export class ConfigServerComponent implements OnInit {
  restartState: ClrLoadingState = ClrLoadingState.DEFAULT;

  constructor(
    private eventSourceService: EventSourceService,
    private systemService: SystemService,
    private notification: NotificationService,
    private errorHandler: ErrorHandlerService,
  ) {}

  ngOnInit() {}

  shutdownServer(): void {
    if (!confirm('Do you really want to shut down the server?')) {
      return;
    }
    this.systemService.shutdown().subscribe(
      ok => {
        this.notification.info('The server is about to shut down. This configuration page will stop working now.');
      },
      this.errorHandler.createErrorHandler('Shutting down the server')
    );
  }

  startEventSources() {
    this.changeEventSourceStates(true, 'event sources started.');
  }

  stopEventSources() {
    this.changeEventSourceStates(false, 'event sources stopped.');
  }

  changeEventSourceStates(running: boolean, messageWhenDone: string) {
    this.restartState = ClrLoadingState.LOADING;
    const restarted: {[s: string]: boolean; } = {};
    const idsDone: string[] = [];

    const checkAll = () => {
      if (Object.keys(restarted).length === 0 || Object.values(restarted).reduce((a, b) => a && b)) {
        this.notification.success(Object.keys(restarted).length + ' ' + messageWhenDone);
        this.restartState = ClrLoadingState.SUCCESS;
      }
    };
    const preMessage = running ? 'Starting event source ' : 'Stopping event source ';
    this.eventSourceService.listInstances('', 0, true).subscribe(
      list => {
        // filter for event sources we can start
        list = list.filter(item => item.service && item.enabled && item.running !== running);
        console.log('Starting/ stopping', list.map(i => i.name + ' (' + i.id + ')'));
        // prepare status collection
        list.forEach(item => restarted[item.id] = false);
        // start or stop the event sources
        list.forEach(item => {
          this.eventSourceService.setInstanceRunning(item.id, running).subscribe(
            success => {
              console.log('Done: ', item.id);
              idsDone.push(item.id);
            },
            this.errorHandler.createErrorHandler(preMessage + item.id),
            () => {
              restarted[item.id] = true;
              checkAll();
            }
          );
        });
        checkAll();
      },
      this.errorHandler.createErrorHandler('Listing event sources', () => {
        this.restartState = ClrLoadingState.ERROR;
      })
    );
  }
}
