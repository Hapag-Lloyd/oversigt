import { Component, OnInit, OnDestroy } from '@angular/core';
import { EventSourceService, EventSourceInstanceInfo, DashboardShortInfo, EventSourceInfo } from 'src/oversigt-client';
import { Router, ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { EventsourceSelectionService } from '../../eventsource-selection.service';
import { NzTreeNode, NzOptionComponent } from 'ng-zorro-antd';

@Component({
  selector: 'app-config-eventsources',
  templateUrl: './config-eventsources.component.html',
  styleUrls: ['./config-eventsources.component.css']
})
export class ConfigEventsourcesComponent implements OnInit, OnDestroy {
  eventSourceInfos: EventSourceInstanceInfo[] = [];
  selectedEventSource: EventSourceInstanceInfo = null;
  treeNodes: NzTreeNode[] = [];
  private subscriptions: Subscription[] = [];
  private selectedEventSourceIdToBeSelected: string = null;

  constructor(
    private eventSourceSelection: EventsourceSelectionService,
    private route: ActivatedRoute,
    private router: Router,
    private ess: EventSourceService,
  ) {
    const _this_ = this;
    this.subscriptions.push(eventSourceSelection.selectedEventSource.subscribe(id => {
      this.selectedEventSource = this.getEventSource(id);
    }));
    this.subscriptions.push(route.url.subscribe(segs => {
      if (route !== null
          && route.snapshot !== null
          && route.snapshot.firstChild !== null
          && route.snapshot.firstChild.params !== null) {
        _this_.selectedEventSource = _this_.getEventSource(route.snapshot.firstChild.params['id']);
        _this_.selectedEventSourceIdToBeSelected = null;
        if (_this_.selectedEventSource === undefined) {
          _this_.selectedEventSourceIdToBeSelected = route.snapshot.firstChild.params['id'];
        }
      } else {
        _this_.selectedEventSource = null;
      }
    }));

    // TODO alle 5 minuten die Liste der EventSources aktualisieren
  }

  ngOnInit() {
    this.initEventSourceInstanceList();
  }

  ngOnDestroy() {
    this.subscriptions.forEach(s => s.unsubscribe());
  }

  private initEventSourceInstanceList() {
    this.ess.listInstances().subscribe(
      infos => {
        this.eventSourceInfos = infos;
        if (this.selectedEventSourceIdToBeSelected !== null) {
          this.selectedEventSource = this.getEventSource(this.selectedEventSourceIdToBeSelected);
          this.selectedEventSourceIdToBeSelected = null;
        }

        this.buildTreeSelectNodes(infos);
      },
      error => {
        console.error(error);
        alert(error);
        // TODO: Error handling
      }
    );
  }

  filterSelectOption(input: string, option: NzOptionComponent): boolean {
    return JSON.stringify(option.nzValue).toLowerCase().includes(input.toLowerCase());
  }

  private buildTreeSelectNodes(infos: EventSourceInstanceInfo[]) {
    function createKnot(title: string, children: NzTreeNode[] = []): NzTreeNode {
      return <NzTreeNode>{level: 0, title: title, isLeaf: false, children: children, isSelectable: false};
    }
    function createLeaf(id: string, title: string, info: EventSourceInstanceInfo): NzTreeNode {
      return <NzTreeNode><any>{level: 1, title: title, key: id, value: info, isLeaf: true, children: []};
    }

    const dashboards = {};
    infos.forEach(info => {
      if (info.usedBy !== null) {
        info.usedBy.forEach(ub => {
          dashboards[ub.id] = ub.title;
        });
      }
    });
    const nodes = Object.keys(dashboards).map(id => createKnot(dashboards[id],
      infos.filter(info => info.usedBy !== null && info.usedBy.length > 0)
      .filter(info => info.usedBy.find(ub => ub.id === id) !== undefined)
      .map(info => createLeaf(info.id, info.name, info))
      ));
    nodes.push(createKnot('<unused>',
                          infos.filter(info => info.usedBy === null || info.usedBy.length === 0)
                                .map(info => createLeaf(info.id, info.name, info))));
    this.treeNodes = nodes;
  }

  private getEventSource(id: string): EventSourceInstanceInfo {
    return this.eventSourceInfos.find(info => info.id === id);
  }

  selectEventSource(id: string | EventSourceInstanceInfo): void {
    this.router.navigateByUrl('/config/eventSources/' + (typeof id === 'string' ? id : id.id));
  }

  removeEventSourceInstance(id: string): void {
    this.eventSourceInfos = this.eventSourceInfos.filter(info => info.id !== id);
  }
}
