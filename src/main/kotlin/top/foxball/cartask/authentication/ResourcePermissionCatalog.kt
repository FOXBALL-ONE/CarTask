package top.foxball.cartask.authentication

import top.foxball.cartask.entity.UserResourcePermission

/** Maps account-level resource grants to the existing endpoint permission vocabulary. */
object ResourcePermissionCatalog {
    val resourcePermissions = setOf(
        "vehicle:read", "vehicle:create", "vehicle:update", "vehicle:delete",
        "vehicle-inout-request:read", "vehicle-inout-request:apply", "vehicle-inout-request:review",
        "vehicle-inout-request:sync", "vehicle-inout-request:export", "vehicle-record:read", "vehicle-record:export",
        "plate:read", "plate:manage", "plate-sync:read", "plate-sync:retry", "plate-sync:reconcile",
        "owner:read", "owner:manage", "owner:sync", "spot:read", "spot:manage", "account:sync",
        "gate-person:read", "gate-person:manage", "gate-person:review", "gate-person:export",
        "gate-person:sensitive-read", "gate-person:department-scope", "person-record:read", "person-record:export",
        "violation:read", "violation:manage", "violation:entry", "violation:export", "violation:review",
    )

    fun permissionsFor(grants: Collection<UserResourcePermission>): Set<String> = buildSet {
        grants.forEach { grant ->
            when (grant.resourceType) {
                UserResourcePermission.ResourceType.VEHICLE -> addAll(
                    listOf(
                        "vehicle:read",
                        "vehicle:create",
                        "vehicle:update",
                        "plate:read",
                        "plate:manage",
                        "owner:read",
                        "owner:manage",
                        "spot:read",
                        "spot:manage",
                        "vehicle-inout-request:read",
                        "vehicle-inout-request:apply",
                        "vehicle-inout-request:export",
                        "vehicle-record:read",
                        "plate-sync:read",
                    ),
                )

                UserResourcePermission.ResourceType.FACE -> addAll(
                    listOf(
                        "gate-person:read",
                        "gate-person:manage",
                        "gate-person:department-scope",
                        "person-record:read",
                        "person-record:export",
                    ),
                )

                UserResourcePermission.ResourceType.VIOLATION -> addAll(
                    listOf("violation:read", "violation:entry"),
                )
            }
            if (grant.reviewEnabled) {
                when (grant.resourceType) {
                    UserResourcePermission.ResourceType.VEHICLE -> add("vehicle-inout-request:review")
                    UserResourcePermission.ResourceType.FACE -> add("gate-person:review")
                    UserResourcePermission.ResourceType.VIOLATION -> add("violation:review")
                }
            }
        }
    }
}
