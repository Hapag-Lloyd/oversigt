import { Component, OnInit } from '@angular/core';
import { EventSourceService } from 'src/oversigt-client';
import { NotificationService } from 'src/app/notification.service';

@Component({
  selector: 'app-config-server',
  templateUrl: './config-server.component.html',
  styleUrls: ['./config-server.component.css']
})
export class ConfigServerComponent implements OnInit {
  restartDrawerVisible = false;
  restartingEventSources = false;
  restartPrecent = 0;

  constructor(
    private ess: EventSourceService,
    private notification: NotificationService,
  ) {}

  ngOnInit() {}

  restartServer(): void {
    this.restartDrawerVisible = false;
    alert('restart');
  }

  startEventSources() {
    this.changeEventSourceStates(true, 'Event sources started.');
  }

  stopEventSources() {
    this.changeEventSourceStates(false, 'Event sources stopped.');
  }

  changeEventSourceStates(running: boolean, messageWhenDone: string) {
    this.restartingEventSources = true;
    const restarted: {[s: string]: boolean; } = {};
    const checkAll = () => {
      this.restartPrecent = Object.values(restarted).filter(i => i).length / Object.keys(restarted).length;
      this.restartingEventSources = Object.values(restarted).reduce((a, b) => a && b);
      if (!this.restartingEventSources) {
        this.notification.success(messageWhenDone);
      }
    };
    this.ess.listInstances().subscribe(
      list => {
        list.forEach(item => restarted[item.id] = false);
        list.forEach(item => {
          this.ess.setInstanceRunning(item.id, running).subscribe(
            success => {
              console.log(item.id, 'done');
            },
            error => {
              console.error(error.error);
              // alert(error);
              // TODO: Error handling
            },
            () => {
              restarted[item.id] = true;
              checkAll();
            }
          );
        });
      },
      error => {
        console.error(error);
        alert(error);
        // TODO: Error handling
      }
    );
    setTimeout(() => {
      this.restartingEventSources = false;
    }, 2000);
  }
}
