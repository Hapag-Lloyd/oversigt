import { Component, OnInit, Input, OnDestroy, TemplateRef } from '@angular/core';
import { SerializableValueService, SerializablePropertyMember } from 'src/oversigt-client';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { NzModalService, NzNotificationService } from 'ng-zorro-antd';

@Component({
  selector: 'app-config-property',
  templateUrl: './config-property.component.html',
  styleUrls: ['./config-property.component.css']
})
export class ConfigPropertyComponent implements OnInit, OnDestroy {
  private subscription: Subscription = null;
  propertyType: string;
  members: SerializablePropertyMember[] = [];
  values: object[] = [];
  valueToCreate = {};

  constructor(
    private route: ActivatedRoute,
    private modalService: NzModalService,
    private notification: NzNotificationService,
    private svs: SerializableValueService,
  ) { }

  ngOnInit() {
    this.notification.config({
      nzPlacement: 'bottomRight'
    });
    this.propertyType = this.route.snapshot.paramMap.get('name');
    this.subscription = this.route.url.subscribe(_ => {
      const newType = this.route.snapshot.paramMap.get('name');
      if (newType !== this.propertyType) {
        this.propertyType = newType;
        this.initComponent();
      }
    });
    this.initComponent();
  }

  ngOnDestroy() {
    if (this.subscription !== null) {
      this.subscription.unsubscribe();
    }
  }

  private setValues(values: object[]): void {
    this.values = values.sort((a, b) => a['name'].toLowerCase() > b['name'].toLowerCase() ? 1 : -1);
  }

  private initComponent() {
    this.valueToCreate = {};
    this.members = [];
    this.svs.readMembers(this.propertyType).subscribe(
      members => {
        this.members = members;
        this.svs.listProperties(this.propertyType).subscribe(
          values => this.setValues(values)
        );
      },
      error => {
        console.error('Cannot load property type "' + this.propertyType + '"' + error);
        this.propertyType = null;
        this.members = [];
      }
    );
  }

  deleteValue(id: number): void {
    const valueToDelete = this.getValue(id);
    this.svs.deleteProperty(this.propertyType, id).subscribe(
      ok => {
        this.setValues(this.values.filter(e => e['id'] !== id));
        this.createSuccessNotification('Deleted', 'Property "' + valueToDelete['name'] + '" has been deleted.');
      },
      error => alert('There was an error while deleting the property: ' + error)
    );
  }

  private getValue(id: number) {
    return this.values.filter(v => v['id'] === id)[0];
  }

  showCreateModal(tplContent: TemplateRef<{}>): void {
    this.valueToCreate = {};
    this.showModal('Create', tplContent, () => new Promise((resolve, fail) => {
        this.svs.createProperty(this.propertyType, this.valueToCreate).subscribe(
          createdValue => {
            this.values.push(createdValue);
            this.setValues(this.values); // sort the stuff...
            this.createSuccessNotification('Created', 'Property "' + createdValue['name'] + '" has been created.');
            resolve();
          },
          error => {
            alert(error.error.errors[0]);
            fail();
          }
        );
      }));
  }

  showEditModal(tplContent: TemplateRef<{}>, id: number): void {
    this.valueToCreate = {};
    const valueToEdit = this.getValue(id);
    for (const member of this.members) {
      this.valueToCreate[member.name] = valueToEdit[member.name];
    }
    this.showModal('Edit', tplContent, () => new Promise((resolve, fail) => {
      this.svs.updateProperty(this.propertyType, id, this.valueToCreate).subscribe(
        changedValue => {
          for (const member of this.members) {
            valueToEdit[member.name] = changedValue[member.name];
          }
          this.setValues(this.values); // sort the stuff...
          this.createSuccessNotification('Saved', 'Changes for property "' + changedValue['name'] + '" have been saved.');
          resolve();
        },
        error => {
          alert(error.error.errors[0]);
          fail();
        }
      );
    }));
  }

  private showModal<T>(verb: string, tplContent: TemplateRef<{}>, promise: () => Promise<T>) {
    this.modalService.create({
      nzTitle: verb + ' ' + this.propertyType + ' entry',
      nzContent: tplContent,
      nzMaskClosable: true,
      nzClosable: false,
      nzOnOk: promise,
      nzOnCancel: () => this.valueToCreate = {}
    });
  }

  private clearBeforeNotifications(): void {
    this.notification.remove();
  }

  private createSuccessNotification(title: string, text: string): void {
    this.notification.success(title, text);
  }
}
