import { Component, OnInit, Input, OnDestroy, TemplateRef } from '@angular/core';
import { SerializableValueService, SerializablePropertyMember } from 'src/oversigt-client';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { NzModalService, NzMessageService } from 'ng-zorro-antd';

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

  showingCreateModal = false;

  constructor(
    private route: ActivatedRoute,
    private modalService: NzModalService,
    private message: NzMessageService,
    private svs: SerializableValueService,
  ) { }

  ngOnInit() {
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
        this.createSuccessNotification('Property "' + valueToDelete['name'] + '" has been deleted.');
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
            this.createSuccessNotification('Property "' + createdValue['name'] + '" has been created.');
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
    const _this_ = this;
    this.valueToCreate = {};
    const valueToEdit = this.getValue(id);
    for (const member of this.members) {
      this.valueToCreate[member.name] = valueToEdit[member.name];
    }
    this.showModal('Edit', tplContent, () => {
      const unfilledArguments = [];
      for (const member of this.members) {
        if (member.required && !_this_.valueToCreate[member.name]) {
          unfilledArguments.push(member.name);
        }
      }
      if (unfilledArguments.length === 0) {
        return new Promise((resolve, fail) => {
          _this_.svs.updateProperty(_this_.propertyType, id, _this_.valueToCreate).subscribe(
            changedValue => {
              for (const member of this.members) {
                valueToEdit[member.name] = changedValue[member.name];
              }
              _this_.setValues(_this_.values); // sort the stuff...
              _this_.createSuccessNotification('Changes for property "' + changedValue['name'] + '" have been saved.');
              resolve();
            },
            error => {
              alert(error.error.errors[0]);
              fail();
            }
          );
        });
      } else {
        this.message.error('Please fill all required fields. The following field(s) are missing: ' + unfilledArguments.join(', '));
        return false;
      }
    });
  }

  private showModal<T>(verb: string, tplContent: TemplateRef<{}>, promise: () => false | Promise<T>) {
    this.modalService.create({
      nzTitle: verb + ' ' + this.propertyType + ' entry',
      nzContent: tplContent,
      nzMaskClosable: true,
      nzClosable: false,
      nzOnOk: promise,
      nzOnCancel: () => this.valueToCreate = {}
    });
  }

  private createSuccessNotification(text: string): void {
    this.message.success(text);
  }
}
