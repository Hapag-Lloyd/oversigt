import { Component, OnInit, OnDestroy, ViewChild, ComponentRef } from '@angular/core';
import { DashboardService, Dashboard, DashboardWidgetService, SystemService, WidgetShortInfo } from 'src/oversigt-client';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { FormControl } from '@angular/forms';
import { ClrLoadingState } from '@clr/angular';
import { getLinkForDashboards } from 'src/app/app.component';
import { NotificationService } from 'src/app/notification.service';
import { ConfigDashboardWidgetComponent } from '../dashboards-widget/config-dashboards-widget.component';

@Component({
  selector: 'app-config-dashboards-edit',
  templateUrl: './config-dashboards-edit.component.html',
  styleUrls: ['./config-dashboards-edit.component.scss']
})
export class ConfigDashboardsEditComponent implements OnInit, OnDestroy {
  private subscriptions: Subscription[] = [];
  private childSubscription: Subscription = null;

  private dashboardId: string = null;
  dashboardTitle = '';
  dashboard: Dashboard = null;
  screensize: number[] = [];
  foregroundColors: string[] = [];
  // TODO: implement chips
  // owners: string[] = [];
  // editors: string[] = [];
  ownersText = '';
  editorsText = '';
  widgetInfos: WidgetShortInfo[] = [];

  // Loading indicator
  saveDashboardState: ClrLoadingState = ClrLoadingState.DEFAULT;
  deleteDashboardState: ClrLoadingState = ClrLoadingState.DEFAULT;
  enableDashboardState: ClrLoadingState = ClrLoadingState.DEFAULT;
  updateRightsState: ClrLoadingState = ClrLoadingState.DEFAULT;

  // for chip editor
  syncUserIdValidators = [];
  asyncUserIdValidators = [];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private dashboardService: DashboardService,
    private dashboardWidgetService: DashboardWidgetService,
    private systemService: SystemService,
    private notification: NotificationService,
  ) {
    const isUserIdValid = (control: FormControl) => {
      return new Promise(resolve => {
        const userid = control.value;
        this.systemService.isUserValid(userid).subscribe(valid => {
          resolve(valid);
        },
        error => {
          resolve(false);
        });
      });
    };
    this.asyncUserIdValidators = [isUserIdValid];
    this.syncUserIdValidators = [(control: FormControl) => control.value.length > 0];
  }

  ngOnInit() {
    this.subscriptions.push(this.route.url.subscribe(_ => {
      this.initComponent();
    }));
  }

  ngOnDestroy() {
    this.subscriptions.forEach(s => s.unsubscribe());
    if (this.childSubscription) {
      this.childSubscription.unsubscribe();
    }
  }

  activateChild(componentRef: ComponentRef<any>): void {
    if (this.childSubscription) {
      this.childSubscription.unsubscribe();
    }
    if (componentRef instanceof ConfigDashboardWidgetComponent) {
      const child: ConfigDashboardWidgetComponent = componentRef;
      this.childSubscription = child.stateChanged.subscribe(event => {
        this.loadWidgetPositions();
      });
    }
  }

  isAddingWidget(): boolean {
    if (this.route.snapshot.children && this.route.snapshot.children[0]) {
      const last = this.route.snapshot.children[0].url.map(s => s.path)[0];
      return last === 'add';
    }
    return false;
  }

  private initComponent(): void {
    // find selected dashboard id
    this.dashboardId = this.route.snapshot.paramMap.get('dashboardId');

    this.dashboardService.readDashboard(this.dashboardId).subscribe(dashboard => {
      this.setDashboard(dashboard);
      // TODO: Error handling
    });
    this.loadWidgetPositions();
  }

  private loadWidgetPositions(): void {
    this.dashboardWidgetService.listWidgets(this.dashboardId).subscribe(widgetInfos => {
      this.widgetInfos = widgetInfos;
      // TODO: Error handling
    });
  }

  private setDashboard(dashboard: Dashboard, withRights: boolean = false): void {
    this.dashboard = dashboard;
    this.dashboardTitle = dashboard.title;
    this.foregroundColors = [dashboard.foregroundColorStart, dashboard.foregroundColorEnd];
    this.screensize = [dashboard.screenWidth, dashboard.screenHeight];
    if (withRights) {
      // this.owners = dashboard.owners;
      // this.editors = dashboard.editors;
      this.ownersText = dashboard.owners.join(', ');
      this.editorsText = dashboard.editors.join(', ');
    }
  }

  saveDashboardSettings(): void {
    this.saveDashboardState = ClrLoadingState.LOADING;

    this.updateDashoard(db => {
      db.foregroundColorStart = this.foregroundColors[0];
      db.foregroundColorEnd   = this.foregroundColors[1];
      db.screenWidth  = this.screensize[0];
      db.screenHeight = this.screensize[1];
      db.backgroundColor = this.dashboard.backgroundColor;
      db.colorScheme = this.dashboard.colorScheme;
      db.columns = this.dashboard.columns;
      db.title = this.dashboard.title;
    }, db => {
      this.setDashboard(db, false);
      this.saveDashboardState = ClrLoadingState.SUCCESS;
      this.notification.success('The dashboard configuration has been saved.');
    }, () => {
      this.saveDashboardState = ClrLoadingState.ERROR;
      this.notification.error('An error occurred while saving the dashboard.');
    });
  }

  deleteDashboard(): void {
    if (!confirm('Do you really want to delete this dashboard?')) {
      return;
    }

    this.dashboardService.deleteDashboard(this.dashboardId).subscribe(ok => {
      this.deleteDashboardState = ClrLoadingState.SUCCESS;
      this.notification.success('The dashboard "' + this.dashboard.title + '"has been deleted.');
      this.router.navigateByUrl(getLinkForDashboards());
    }, error => {
      this.deleteDashboardState = ClrLoadingState.ERROR;
      console.log(error);
      alert(error);
      // TODO: error handling
    });
  }

  enableDashboard(enabled: boolean): void {
    this.enableDashboardState = ClrLoadingState.LOADING;
    this.updateDashoard(db => {
      db.enabled = enabled;
    }, db => {
      this.enableDashboardState = ClrLoadingState.SUCCESS;
      this.dashboard.enabled = db.enabled;
      if (enabled) {
        this.notification.success('The dashboard "' + db.title + '" has been enabled.');
      } else {
        this.notification.success('The dashboard "' + db.title + '" has been disabled.');
      }
    }, () => {
      this.enableDashboardState = ClrLoadingState.ERROR;
      this.notification.error('An error occurred while changing the dashboard enabled state.');
    });
  }

  updateOwnersAndEditors(): void {
    this.updateRightsState = ClrLoadingState.LOADING;
    const owners: string[] = this.findUsersIds(this.ownersText);
    const editors: string[] = this.findUsersIds(this.editorsText);

    this.updateDashoard(db => {
      db.owners = owners;
      db.editors = editors;
    }, db => {
      this.ownersText = db.owners.join(', ');
      this.editorsText = db.editors.join(', ');
      this.updateRightsState = ClrLoadingState.SUCCESS;
      this.notification.success('The rights have been updated.');
    }, () => {
      this.updateRightsState = ClrLoadingState.ERROR;
      this.notification.error('An error occurred while changing the dashboard rights.');
    });
  }

  private updateDashoard(update: (dashboard: Dashboard) => void, ok: (dashboard: Dashboard) => void, fail: () => void): void {
    this.dashboardService.readDashboard(this.dashboardId).subscribe(
      dashboard => {
        update(dashboard);
        this.dashboardService.updateDashboard(this.dashboardId, dashboard).subscribe(
          newDashboardData => {
            ok(newDashboardData);
          }, error => {
            // TODO: error handling
            this.updateRightsState = ClrLoadingState.ERROR;
            console.log(error);
            alert(error);
            fail();
          }
        );
      }, error => {
        // TODO: error handling
        this.updateRightsState = ClrLoadingState.ERROR;
        console.log(error);
        alert(error);
        fail();
      }
    );
  }


  private findUsersIds(text: string): string[] {
    const regex = /[A-Za-z0-9]+/gi;
    const array: string[] = [];
    let result: RegExpExecArray | string[];
    while ( (result = regex.exec(text)) ) {
      array.push(result[0]);
    }
    return array;
  }

  countColumns(): number {
    return Math.max(this.dashboard.columns, Math.max(...this.widgetInfos.map(i => i.posX + i.sizeX)) - 1);
  }

  countRows(): number {
    return Math.max(...this.widgetInfos.map(i => i.posY + i.sizeY)) - 1;
  }
}
